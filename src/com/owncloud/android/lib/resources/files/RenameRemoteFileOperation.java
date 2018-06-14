/* ownCloud Android Library is available under MIT license
 *   Copyright (C) 2016 ownCloud GmbH.
 *   
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *   
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *   
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, 
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND 
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS 
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN 
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN 
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */

package com.owncloud.android.lib.resources.files;

import java.io.File;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.http.HttpConstants;
import com.owncloud.android.lib.common.http.methods.webdav.MoveMethod;
import com.owncloud.android.lib.common.network.WebdavUtils;
import com.owncloud.android.lib.common.operations.RemoteOperation;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.status.OwnCloudVersion;

import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;

import okhttp3.HttpUrl;


/**
 * Remote operation performing the rename of a remote file or folder in the ownCloud server.
 *
 * @author David A. Velasco
 * @author masensio
 */
public class RenameRemoteFileOperation extends RemoteOperation {

    private static final String TAG = RenameRemoteFileOperation.class.getSimpleName();

    private static final int RENAME_READ_TIMEOUT = 600000;
    private static final int RENAME_CONNECTION_TIMEOUT = 5000;

    private String mOldName;
    private String mOldRemotePath;
    private String mNewName;
    private String mNewRemotePath;


    /**
     * Constructor
     *
     * @param oldName       Old name of the file.
     * @param oldRemotePath Old remote path of the file.
     * @param newName       New name to set as the name of file.
     * @param isFolder      'true' for folder and 'false' for files
     */
    public RenameRemoteFileOperation(String oldName, String oldRemotePath, String newName,
                                     boolean isFolder) {
        mOldName = oldName;
        mOldRemotePath = oldRemotePath;
        mNewName = newName;

        String parent = (new File(mOldRemotePath)).getParent();
        parent = (parent.endsWith(FileUtils.PATH_SEPARATOR)) ? parent : parent +
            FileUtils.PATH_SEPARATOR;
        mNewRemotePath = parent + mNewName;
        if (isFolder) {
            mNewRemotePath += FileUtils.PATH_SEPARATOR;
        }
    }

    /**
     * Performs the rename operation.
     *
     * @param client Client object to communicate with the remote ownCloud server.
     */
    @Override
    protected RemoteOperationResult run(OwnCloudClient client) {
        RemoteOperationResult result = null;

        MoveMethod move = null;

        OwnCloudVersion version = client.getOwnCloudVersion();
        boolean versionWithForbiddenChars =
            (version != null && version.isVersionWithForbiddenCharacters());
        boolean noInvalidChars = FileUtils.isValidPath(mNewRemotePath, versionWithForbiddenChars);

        if (noInvalidChars) {
            try {
                if (mNewName.equals(mOldName)) {
                    return new RemoteOperationResult(ResultCode.OK);
                }

                if (targetPathIsUsed(client)) {
                    return new RemoteOperationResult(ResultCode.INVALID_OVERWRITE);
                }

                move = new MoveMethod(HttpUrl.parse(client.getWebdavUri() +
                    WebdavUtils.encodePath(mOldRemotePath)),
                    client.getWebdavUri() + WebdavUtils.encodePath(mNewRemotePath), false);
                //TODO: client.execute(move, RENAME_READ_TIMEOUT, RENAME_CONNECTION_TIMEOUT);
                final int status = client.executeHttpMethod(move);
                if(status == HttpConstants.HTTP_CREATED || status == HttpConstants.HTTP_NO_CONTENT) {
                    result = new RemoteOperationResult(ResultCode.OK);
                } else {
                    result = new RemoteOperationResult(move);
                }
                Log_OC.i(TAG, "Rename " + mOldRemotePath + " to " + mNewRemotePath + ": " +
                            result.getLogMessage()
                );
                client.exhaustResponse(move.getResponseAsStream());

            } catch (Exception e) {
                result = new RemoteOperationResult(e);
                Log_OC.e(TAG, "Rename " + mOldRemotePath + " to " +
                    ((mNewRemotePath == null) ? mNewName : mNewRemotePath) + ": " +
                    result.getLogMessage(), e);

            }

        } else {
            result = new RemoteOperationResult(ResultCode.INVALID_CHARACTER_IN_NAME);
        }

        return result;
    }

    /**
     * Checks if a file with the new name already exists.
     *
     * @return      'True' if the target path is already used by an existing file.
     */
    private boolean targetPathIsUsed(OwnCloudClient client) {
        ExistenceCheckRemoteOperation existenceCheckRemoteOperation =
            new ExistenceCheckRemoteOperation(mNewRemotePath, false);
        RemoteOperationResult exists = existenceCheckRemoteOperation.run(client);
        return exists.isSuccess();
    }

}

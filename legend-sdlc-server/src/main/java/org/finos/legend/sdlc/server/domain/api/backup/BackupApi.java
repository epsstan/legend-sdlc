// Copyright 2020 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.server.domain.api.backup;

public interface BackupApi
{
    /**
     * Discard the backup workspace
     *
     * @param projectId    project id
     * @param workspaceId  id of backup workspace
     */
    void discardBackupWorkspace(String projectId, String workspaceId);

    /**
     * Recover the backup workspace
     *
     * @param projectId      project id
     * @param workspaceId    workspace id
     * @param forceRecovery  flag indicating that if the workspace and its backup both exist, we will override the workspace by its backup
     */
    void recoverBackup(String projectId, String workspaceId, boolean forceRecovery);
}

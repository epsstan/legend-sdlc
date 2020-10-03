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

package org.finos.legend.sdlc.server.project;

import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.sdlc.domain.model.revision.Revision;
import org.finos.legend.sdlc.domain.model.version.VersionId;
import org.finos.legend.sdlc.server.SimpleInMemoryVCS;

import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InMemoryProjectFileAccessProvider implements ProjectFileAccessProvider
{
    private final Map<String, SimpleInMemoryVCS> projects = Maps.mutable.empty();
    private final String defaultAuthor;
    private final String defaultCommitter;

    public InMemoryProjectFileAccessProvider(String defaultAuthor, String defaultCommitter)
    {
        this.defaultAuthor = defaultAuthor;
        this.defaultCommitter = defaultCommitter;
    }

    // File Access Context

    @Override
    public FileAccessContext getFileAccessContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType)
    {
        switch (workspaceAccessType)
        {
            case WORKSPACE:
            {
                return new AbstractFileAccessContext(revisionId)
                {
                    @Override
                    protected SimpleInMemoryVCS getContextVCS()
                    {
                        return getVCS(projectId, workspaceId);
                    }
                };
            }
            default:
            {
                throw new UnsupportedOperationException("Special workspace access type is not supported for getting file access context");
            }
        }
    }

    @Override
    public FileAccessContext getFileAccessContext(String projectId, VersionId versionId)
    {
        return new AbstractFileAccessContext()
        {
            @Override
            protected SimpleInMemoryVCS getContextVCS()
            {
                return getVCS(projectId, versionId);
            }
        };
    }

    // Revision Access Context

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, String workspaceId, String path, WorkspaceAccessType workspaceAccessType)
    {
        switch (workspaceAccessType)
        {
            case WORKSPACE:
            {
                return new AbstractRevisionAccessContext(path)
                {
                    @Override
                    protected SimpleInMemoryVCS getContextVCS()
                    {
                        return getVCS(projectId, workspaceId);
                    }
                };
            }
            default:
            {
                throw new UnsupportedOperationException("Special workspace access type is not supported for getting revision access context");
            }
        }
    }

    @Override
    public RevisionAccessContext getRevisionAccessContext(String projectId, VersionId versionId, String path)
    {
        return new AbstractRevisionAccessContext(path)
        {
            @Override
            protected SimpleInMemoryVCS getContextVCS()
            {
                return getVCS(projectId, versionId);
            }
        };
    }

    // File Modification Context

    @Override
    public FileModificationContext getFileModificationContext(String projectId, String workspaceId, String revisionId, WorkspaceAccessType workspaceAccessType)
    {
        switch (workspaceAccessType)
        {
            case WORKSPACE:
            {
                return new InMemoryFileModificationContext(projectId, workspaceId, revisionId);
            }
            default:
            {
                throw new UnsupportedOperationException("Special workspace access type is not supported for getting file modification context");
            }
        }
    }

    public void createWorkspace(String projectId, String workspaceId)
    {
        SimpleInMemoryVCS vcs = getVCS(projectId);
        vcs.newBranch(workspaceId);
    }

    public void deleteWorkspace(String projectId, String workspaceId)
    {
        SimpleInMemoryVCS vcs = getVCS(projectId);
        vcs.deleteBranch(workspaceId);
    }

    public void commitWorkspace(String projectId, String workspaceId)
    {
        SimpleInMemoryVCS vcs = getVCS(projectId);
        vcs.mergeBranchToTrunk(workspaceId);
        vcs.deleteBranch(workspaceId);
    }

    public void createVersion(String projectId, VersionId versionId)
    {
        SimpleInMemoryVCS vcs = getVCS(projectId);
        vcs.newVersionTag(versionId);
    }

    private SimpleInMemoryVCS getVCS(String projectId)
    {
        return this.projects.computeIfAbsent(projectId, pid -> new SimpleInMemoryVCS());
    }

    private SimpleInMemoryVCS getVCS(String projectId, String workspaceId)
    {
        SimpleInMemoryVCS vcs = this.projects.computeIfAbsent(projectId, pid -> new SimpleInMemoryVCS());
        if (workspaceId != null)
        {
            vcs = vcs.getBranch(workspaceId);
            if (vcs == null)
            {
                throw new RuntimeException("Unknown workspace in project " + projectId + ": " + workspaceId);
            }
        }
        return vcs;
    }

    private SimpleInMemoryVCS getVCS(String projectId, VersionId versionId)
    {
        SimpleInMemoryVCS vcs = getVCS(projectId).getVersionTag(versionId);
        if (vcs == null)
        {
            throw new RuntimeException("Unknown version in project " + projectId + ": " + versionId.toVersionIdString());
        }
        return vcs;
    }

    public class InMemoryFileModificationContext implements FileModificationContext
    {
        private final String projectId;
        private final String workspaceId;
        private final String revisionId;
        private String defaultAuthor = InMemoryProjectFileAccessProvider.this.defaultAuthor;
        private String defaultCommitter = InMemoryProjectFileAccessProvider.this.defaultCommitter;

        private InMemoryFileModificationContext(String projectId, String workspaceId, String revisionId)
        {
            this.projectId = projectId;
            this.workspaceId = workspaceId;
            this.revisionId = revisionId;
        }

        @Override
        public Revision submit(String message, List<? extends ProjectFileOperation> operations)
        {
            return submit(null, null, message, operations);
        }

        public Revision submit(String committer, String message, List<? extends ProjectFileOperation> operations)
        {
            return submit(null, committer, message, operations);
        }

        public Revision submit(String author, String committer, String message, List<? extends ProjectFileOperation> operations)
        {
            SimpleInMemoryVCS vcs = getVCS(this.projectId, this.workspaceId);
            if ((this.revisionId != null) && !this.revisionId.equals(vcs.getLatestRevision().getId()))
            {
                throw new RuntimeException("Expected to submit against revision " + this.revisionId + ", found " + vcs.getLatestRevision().getId());
            }
            SimpleInMemoryVCS.CommitBuilder commitBuilder = vcs.newCommitBuilder((author == null) ? this.defaultAuthor : author);
            for (ProjectFileOperation operation : operations)
            {
                if (operation instanceof ProjectFileOperation.AddFile)
                {
                    commitBuilder.add(operation.getPath(), ((ProjectFileOperation.AddFile) operation).getContent());
                }
                else if (operation instanceof ProjectFileOperation.DeleteFile)
                {
                    commitBuilder.delete(operation.getPath());
                }
                else if (operation instanceof ProjectFileOperation.ModifyFile)
                {
                    commitBuilder.modify(operation.getPath(), ((ProjectFileOperation.ModifyFile) operation).getNewContent());
                }
                else if (operation instanceof ProjectFileOperation.MoveFile)
                {
                    ProjectFileOperation.MoveFile moveOperation = (ProjectFileOperation.MoveFile) operation;
                    commitBuilder.move(moveOperation.getPath(), moveOperation.getNewPath(), moveOperation.getNewContent());
                }
                else
                {
                    throw new IllegalArgumentException("Unhandled operation: " + operation);
                }
            }
            if ((this.revisionId != null) && !this.revisionId.equals(vcs.getLatestRevision().getId()))
            {
                throw new RuntimeException("Expected to submit against revision " + this.revisionId + ", found " + vcs.getLatestRevision().getId());
            }
            SimpleInMemoryVCS.Revision revision = commitBuilder.commit((committer == null) ? this.defaultCommitter : committer, message);
            return new VCSRevisionWrapper(revision);
        }

        public String getDefaultAuthor()
        {
            return this.defaultAuthor;
        }

        public void setDefaultAuthor(String author)
        {
            this.defaultAuthor = author;
        }

        public String getDefaultCommitter()
        {
            return this.defaultCommitter;
        }

        public void setDefaultCommitter(String committer)
        {
            this.defaultCommitter = committer;
        }
    }

    private static class VCSFileWrapper implements ProjectFile
    {
        private final SimpleInMemoryVCS.File file;

        private VCSFileWrapper(SimpleInMemoryVCS.File file)
        {
            this.file = file;
        }

        @Override
        public String getPath()
        {
            return this.file.getPath();
        }

        @Override
        public InputStream getContentAsInputStream()
        {
            return this.file.getContentAsStream();
        }

        @Override
        public byte[] getContentAsBytes()
        {
            return this.file.getContent();
        }

        @Override
        public String getContentAsString()
        {
            return this.file.getContentAsString();
        }

        @Override
        public boolean equals(Object other)
        {
            return (this == other) || ((other instanceof VCSFileWrapper) && Objects.equals(this.file, ((VCSFileWrapper) other).file));
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.file);
        }
    }

    private static class VCSRevisionWrapper implements Revision
    {
        private final SimpleInMemoryVCS.Revision revision;

        private VCSRevisionWrapper(SimpleInMemoryVCS.Revision revision)
        {
            this.revision = revision;
        }

        @Override
        public String getId()
        {
            return this.revision.getId();
        }

        @Override
        public String getAuthorName()
        {
            return this.revision.getAuthor();
        }

        @Override
        public Instant getAuthoredTimestamp()
        {
            return this.revision.getTimestamp();
        }

        @Override
        public String getCommitterName()
        {
            return this.revision.getCommitter();
        }

        @Override
        public Instant getCommittedTimestamp()
        {
            return this.revision.getTimestamp();
        }

        @Override
        public String getMessage()
        {
            return this.revision.getMessage();
        }

        @Override
        public boolean equals(Object other)
        {
            return (this == other) || ((other instanceof VCSRevisionWrapper) && Objects.equals(this.revision, ((VCSRevisionWrapper) other).revision));
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.revision);
        }

        @Override
        public String toString()
        {
            return "<Revision " + getId() + ">";
        }
    }

    private abstract static class AbstractFileAccessContext implements FileAccessContext
    {
        private final String revisionId;

        protected AbstractFileAccessContext(String revisionId)
        {
            this.revisionId = revisionId;
        }

        protected AbstractFileAccessContext()
        {
            this(null);
        }

        @Override
        public Stream<ProjectFile> getFilesInDirectory(String directory)
        {
            Stream<ProjectFile> stream = getContextVCS().getFiles(this.revisionId).map(VCSFileWrapper::new);
            String canonicalDirectory = directory.endsWith("/") ? directory : (directory + "/");
            if (!"/".equals(canonicalDirectory))
            {
                stream = stream.filter(f -> f.getPath().startsWith(canonicalDirectory));
            }
            return stream;
        }

        @Override
        public ProjectFile getFile(String path)
        {
            SimpleInMemoryVCS.File file = getContextVCS().getFile(path, this.revisionId);
            return (file == null) ? null : new VCSFileWrapper(file);
        }

        protected abstract SimpleInMemoryVCS getContextVCS();
    }

    private abstract static class AbstractRevisionAccessContext implements RevisionAccessContext
    {
        private final String path;

        protected AbstractRevisionAccessContext(String path)
        {
            this.path = path;
        }

        @Override
        public Revision getBaseRevision()
        {
            // TODO: when we need to test this then we will need to implement this
            throw new UnsupportedOperationException("Getting base revision is not supported");
        }

        @Override
        public Revision getCurrentRevision()
        {
            SimpleInMemoryVCS.Revision revision;
            if (this.path == null)
            {
                revision = getContextVCS().getLatestRevision();
            }
            else
            {
                revision = getContextVCS().getRevisions(Collections.singletonList(this.path), null, null, 1).findFirst().orElse(null);
            }
            return (revision == null) ? null : new VCSRevisionWrapper(revision);
        }

        @Override
        public Revision getRevision(String revisionId)
        {
            SimpleInMemoryVCS.Revision revision = getContextVCS().getRevision(revisionId);
            return ((revision == null) || ((this.path != null) && !revision.isPathAffectedByRevision(this.path))) ? null : new VCSRevisionWrapper(revision);
        }

        @Override
        public Stream<Revision> getAllRevisions(Predicate<? super Revision> predicate, Instant since, Instant until, Integer limit)
        {
            Stream<Revision> stream = getContextVCS().getRevisions((this.path == null) ? null : Collections.singletonList(this.path), since, until, null).map(VCSRevisionWrapper::new);
            if (predicate != null)
            {
                stream = stream.filter(predicate);
            }
            if (limit != null)
            {
                stream = stream.limit(limit);
            }
            return stream;
        }

        protected abstract SimpleInMemoryVCS getContextVCS();
    }
}
/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.uberfire.java.nio.fs.jgit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;
import org.uberfire.java.nio.fs.jgit.util.Git;
import org.uberfire.java.nio.fs.jgit.util.commands.Commit;
import org.uberfire.java.nio.fs.jgit.util.commands.CreateRepository;
import org.uberfire.java.nio.fs.jgit.util.commands.ListRefs;
import org.uberfire.java.nio.fs.jgit.util.commands.SubdirectoryClone;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

public class JGitSubdirectoryCloneTest extends AbstractTestInfra {

    private static final String TARGET_GIT = "target/target",
            SOURCE_GIT = "source/source";

    @Test
    public void cloneSubdirectorySingleBranch() throws Exception {
        final File parentFolder = createTempDirectory();

        final File sourceDir = new File(parentFolder,
                                        SOURCE_GIT + ".git");

        final File targetDir = new File(parentFolder,
                                        TARGET_GIT + ".git");

        final Git origin = gitRepo(sourceDir);
        commit(origin, "master", "first", content("dir1/file.txt", "foo"));
        commit(origin, "master", "second", content("dir2/file2.txt", "bar"));
        commit(origin, "master", "third", content("file3.txt", "moogah"));

        final Git cloned = new SubdirectoryClone(targetDir,
                                                 sourceDir.getAbsoluteFile().toURI().toString(),
                                                 "dir1",
                                                 singletonList("master"),
                                                 CredentialsProvider.getDefault(),
                                                 null).execute();

        assertThat(origin.getRepository().getRemoteNames()).isEmpty();

        assertThat(cloned).isNotNull();
        assertThat(listRefs(cloned)).hasSize(1);

        final List<RevCommit> cloneCommits = getCommits(cloned, "master");
        assertThat(cloneCommits).hasSize(1);

        final RevCommit clonedCommit = cloneCommits.get(0);
        final RevCommit originCommit = getCommits(origin, "master").get(2); // Ordered children first

        assertClonedCommitData(origin, "dir1", clonedCommit, originCommit);
    }

    @Test
    public void cloneSubdirectoryMultipleBranches() throws Exception {
        final File parentFolder = createTempDirectory();

        final File sourceDir = new File(parentFolder,
                                        SOURCE_GIT + ".git");

        final File targetDir = new File(parentFolder,
                                        TARGET_GIT + ".git");

        final Git origin = gitRepo(sourceDir);
        commit(origin,
               "master",
               "first",
               content("dir1/file.txt", "foo"),
               content("dir2/file2.txt", "bar"),
               content("file3.txt", "moogah"));

        branch(origin, "master", "dev");
        commit(origin,
               "dev",
               "second",
               content("dir1/file.txt", "foo1"),
               content("file3.txt", "bar1"));

        branch(origin, "master", "ignored");
        commit(origin,
               "ignored",
               "third",
               content("dir1/file.txt", "foo2"));

        final Git cloned = new SubdirectoryClone(targetDir,
                                                 sourceDir.getAbsoluteFile().toURI().toString(),
                                                 "dir1",
                                                 asList("master", "dev"),
                                                 CredentialsProvider.getDefault(),
                                                 null).execute();

        assertThat(cloned).isNotNull();
        final Set<String> clonedRefs = listRefs(cloned).stream()
                                                       .map(ref -> ref.getName())
                                                       .collect(toSet());
        assertThat(clonedRefs).hasSize(2);
        assertThat(clonedRefs).containsExactly("refs/heads/master", "refs/heads/dev");


        // Check master commits
        {
            final List<RevCommit> cloneCommits = getCommits(cloned, "master");
            assertThat(cloneCommits).hasSize(1);
            assertClonedCommitData(origin, "dir1", cloneCommits.get(0), getCommits(origin, "master").get(0));
        }

        // Check dev commits
        {
            final List<RevCommit> cloneCommits = getCommits(cloned, "dev");
            assertThat(cloneCommits).hasSize(2);

            final List<RevCommit> originCommits = getCommits(origin, "dev");
            assertClonedCommitData(origin, "dir1", cloneCommits.get(0), originCommits.get(0));
            assertClonedCommitData(origin, "dir1", cloneCommits.get(1), originCommits.get(1));
        }
    }

    @Test
    public void cloneSubdirectoryWithMergeCommit() throws Exception {
        final File parentFolder = createTempDirectory();

        final File sourceDir = new File(parentFolder,
                                        SOURCE_GIT + ".git");

        final File targetDir = new File(parentFolder,
                                        TARGET_GIT + ".git");

        final Git origin = gitRepo(sourceDir);
        commit(origin,
               "master",
               "first",
               content("dir1/file.txt", "foo"),
               content("dir2/file2.txt", "bar"),
               content("file3.txt", "moogah"));

        branch(origin, "master", "dev");
        commit(origin,
               "dev",
               "second",
               content("dir1/file.txt", "foo1"),
               content("file3.txt", "bar1"));

        commit(origin,
               "master",
               "another",
               content("dir1/file2.txt", "blah"));

        mergeCommit(origin,
                    "master",
                    "dev",
                    content("dir1/file.txt", "merged value!"),
                    content("dir2/file2.txt", "merged value!"),
                    content("file3.txt", "merged value!"));

        final Git cloned = new SubdirectoryClone(targetDir,
                                                 sourceDir.getAbsoluteFile().toURI().toString(),
                                                 "dir1",
                                                 asList("master", "dev"),
                                                 CredentialsProvider.getDefault(),
                                                 null).execute();

        assertThat(cloned).isNotNull();
        final Set<String> clonedRefs = listRefs(cloned).stream()
                                                       .map(ref -> ref.getName())
                                                       .collect(toSet());
        assertThat(clonedRefs).hasSize(2);
        assertThat(clonedRefs).containsExactly("refs/heads/master", "refs/heads/dev");

        // Check master commits
        {
            final List<RevCommit> cloneCommits = getCommits(cloned, "master");
            assertThat(cloneCommits).hasSize(4); // 2 on master + 1 on dev + 1 merge commit

            final List<RevCommit> originCommits = getCommits(origin, "master");
            assertClonedCommitData(origin, "dir1", cloneCommits.get(0), originCommits.get(0));
            assertClonedCommitData(origin, "dir1", cloneCommits.get(1), originCommits.get(1));
            assertClonedCommitData(origin, "dir1", cloneCommits.get(2), originCommits.get(2));
            assertClonedCommitData(origin, "dir1", cloneCommits.get(3), originCommits.get(3));

            // Check that we preserved the topology of commits.
            assertThat(cloneCommits.get(0).getParentCount()).isEqualTo(2);
            assertThat(cloneCommits.get(1).getParentCount()).isEqualTo(1);
            assertThat(cloneCommits.get(2).getParentCount()).isEqualTo(1);
            assertThat(cloneCommits.get(3).getParentCount()).isEqualTo(0);
        }

        // Check dev commits
        {
            final List<RevCommit> cloneCommits = getCommits(cloned, "dev");
            assertThat(cloneCommits).hasSize(2);

            final List<RevCommit> originCommits = getCommits(origin, "dev");
            assertClonedCommitData(origin, "dir1", cloneCommits.get(0), originCommits.get(0));
            assertClonedCommitData(origin, "dir1", cloneCommits.get(1), originCommits.get(1));
        }
    }

    private void assertClonedCommitData(final Git origin,
                                        final String subdirectory,
                                        final RevCommit clonedCommit,
                                        final RevCommit originCommit) throws Exception {
        assertThat(clonedCommit.getFullMessage()).isEqualTo(originCommit.getFullMessage());

        final PersonIdent authorIdent = clonedCommit.getAuthorIdent();
        final PersonIdent commiterIdent = clonedCommit.getCommitterIdent();
        assertThat(authorIdent).isEqualTo(commiterIdent);
        assertThat(authorIdent.getName()).isEqualTo("name");
        assertThat(authorIdent.getEmailAddress()).isEqualTo("name@example.com");

        final ObjectId originDirId = findIdForPath(origin, originCommit, subdirectory);
        final ObjectId clonedTreeId = clonedCommit.getTree().getId();
        assertThat(clonedTreeId).isEqualTo(originDirId);
    }

    private Ref branch(Git origin, String source, String target) throws Exception {
        final Repository repo = origin.getRepository();
        return org.eclipse.jgit.api.Git.wrap(repo)
                                       .branchCreate()
                                       .setName(target)
                                       .setStartPoint(source)
                                       .call();
    }

    private ObjectId findIdForPath(final Git origin, final RevCommit originMasterTip, final String searchPath) throws Exception {
        try (TreeWalk treeWalk = new TreeWalk(origin.getRepository())) {
            final int treeId = treeWalk.addTree(originMasterTip.getTree());
            treeWalk.setRecursive(false);
            final CanonicalTreeParser treeParser = treeWalk.getTree(treeId, CanonicalTreeParser.class);
            while (treeWalk.next()) {
                final String path = treeParser.getEntryPathString();
                if (path.equals(searchPath)) {
                    return treeParser.getEntryObjectId();
                }
            }
        }

        throw new AssertionError(String.format("Could not find path [%s] in commit [%s].", searchPath, originMasterTip.name()));
    }

    private List<RevCommit> getCommits(final Git git, String branch) throws Exception {
        List<RevCommit> commits = new ArrayList<>();
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            final RevCommit branchTip = revWalk.parseCommit(git.getRepository().resolve(branch));
            revWalk.markStart(branchTip);
            revWalk.sort(RevSort.TOPO);
            final Iterator<RevCommit> iter = revWalk.iterator();
            while (iter.hasNext()) {
                commits.add(iter.next());
            }
        }
        return commits;
    }

    private List<Ref> listRefs(final Git cloned) {
        return new ListRefs(cloned.getRepository()).execute();
    }

    private Git gitRepo(File gitSource) {
        return new CreateRepository(gitSource).execute().get();
    }

    private void commit(final Git origin, String branchName, String message, TestFile... testFiles) throws IOException {
        final Map<String, File> data = Arrays.stream(testFiles)
                                             .collect(toMap(f -> f.path, f -> tmpFile(f.content)));
        new Commit(origin,
                   branchName,
                   "name",
                   "name@example.com",
                   message,
                   null,
                   null,
                   false,
                   data).execute();
    }

    /*
     * Unfortunately there is no easier way to write a commit with multiple parents.
     */
    private void mergeCommit(final Git origin,
                             final String targetBranchName,
                             final String sourceBranchName,
                             final TestFile... testFiles) throws Exception {
        final Repository repo = origin.getRepository();
        final org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.wrap(repo);

        final ObjectId targetId = repo.resolve(targetBranchName);
        final ObjectId sourceId = repo.resolve(sourceBranchName);

        final DirCache dc = DirCache.newInCore();
        final DirCacheEditor editor = dc.editor();

        try (ObjectInserter inserter = repo.newObjectInserter()) {
            final ObjectId treeId = writeTestFilesToTree(dc, editor, inserter, testFiles);
            final ObjectId commitId = writeCommit(inserter, treeId, targetId, sourceId);
            updateBranch(targetBranchName, git, commitId);
        }
    }

    private void updateBranch(final String targetBranchName,
                             final org.eclipse.jgit.api.Git git,
                             final ObjectId commitId) throws Exception {
        git.branchCreate()
           .setName(targetBranchName)
           .setStartPoint(commitId.name())
           .setForce(true)
           .call();
    }

    private ObjectId writeCommit(final ObjectInserter inserter, final ObjectId commitTreeId, final ObjectId... parentIds) throws IOException {
        final CommitBuilder builder = new CommitBuilder();
        builder.setAuthor(new PersonIdent("name", "name@example.com"));
        builder.setCommitter(new PersonIdent("name", "name@example.com"));
        builder.setTreeId(commitTreeId);
        builder.setMessage("merge commit");
        builder.setParentIds(parentIds);

        final ObjectId commitId = inserter.insert(builder);
        return commitId;
    }

    private ObjectId writeTestFilesToTree(final DirCache dc,
                                          final DirCacheEditor editor,
                                          ObjectInserter inserter,
                                          final TestFile... testFiles) throws Exception {
        for (TestFile data : testFiles) {
            writeBlob(editor, inserter, data);
        }
        editor.finish();
        final ObjectId commitTreeId = dc.writeTree(inserter);
        return commitTreeId;
    }

    private void writeBlob(final DirCacheEditor editor, ObjectInserter inserter, TestFile data) throws IOException {
        final ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, data.content.length(), IOUtils.toInputStream(data.content, "UTF-8"));
        editor.add(new PathEdit(data.path) {
            @Override
            public void apply(DirCacheEntry ent) {
                ent.setFileMode(FileMode.REGULAR_FILE);
                ent.setObjectId(blobId);
            }
        });
    }

    private File tmpFile(final String content) {
        try {
            return tempFile(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TestFile content(final String path, final String content) {
        return new TestFile(path, content);
    }

    private static class TestFile {

        final String path;
        final String content;

        TestFile(final String path, final String content) {
            this.path = path;
            this.content = content;
        }
    }
}

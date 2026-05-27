package com.cocode.vcode.ide.git.repository;

import com.cocode.vcode.ide.git.model.BranchItem;
import com.cocode.vcode.ide.git.model.CommitItem;
import com.cocode.vcode.ide.git.model.GitFileItem;
import com.cocode.vcode.ide.utils.DateUtils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Main local repository controller interacting directly with the JGit engine layer.
 * Implements comprehensive version control mechanics, managing work tree staging matrices,
 * commit building, revision log histories, diff generation, reset workflows, and branch orchestration.
 */
public class GitRepository {
    private Git git;
    private String configuredDefaultBranch = "main";
    private File repoDir;

    /**
     * Configures the initial tracking branch naming preference used during fresh repository setups.
     */
    public void setConfiguredDefaultBranch(String branchName) {
        if (branchName != null && !branchName.trim().isEmpty()) {
            this.configuredDefaultBranch = branchName.trim();
        }
    }

    /**
     * Attaches to an existing repository on disk or sets up a brand new one
     * using the configured fallback branch configurations.
     * @param projectDir The root folder tracking active project files.
     */
    public void openRepository(File projectDir) throws Exception {
        this.repoDir = projectDir;
        File gitDir = new File(projectDir, ".git");
        if (gitDir.exists()) {
            git = Git.open(projectDir);
        } else {
            git = Git.init()
                    .setDirectory(projectDir)
                    .setInitialBranch(configuredDefaultBranch)
                    .call();
        }
        ensureInternalFilesIgnored(gitDir);
    }

    public File getRepoDir() {
        return repoDir;
    }

    /**
     * Automatically reviews and updates the hidden .git/info/exclude configuration sheet.
     * Prevents internal, non-source application tracking data (such as session maps or
     * metadata descriptors) from polluting user version logs without littering the .gitignore file.
     */
    private void ensureInternalFilesIgnored(File gitDir) {
        try {
            File infoDir = new File(gitDir, "info");
            if (!infoDir.exists()) {
                infoDir.mkdirs();
            }

            File excludeFile = new File(infoDir, "exclude");
            StringBuilder content = new StringBuilder();

            if (excludeFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(excludeFile), java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
            }

            boolean modified = false;
            String[] internalFiles = {"session.json", "project_meta.json"};
            for (String file : internalFiles) {
                if (!content.toString().contains(file)) {
                    content.append(file).append("\n");
                    modified = true;
                }
            }

            if (modified) {
                try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                        new java.io.OutputStreamWriter(new java.io.FileOutputStream(excludeFile), java.nio.charset.StandardCharsets.UTF_8))) {
                    writer.write(content.toString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Inspects index cache trees to gather all components successfully added to the staged index.
     */
    public List<GitFileItem> getStagedFiles() throws Exception {
        Status status = git.status().call();
        List<GitFileItem> items = new ArrayList<>();
        addFiles(items, status.getAdded(), "A", true);
        addFiles(items, status.getChanged(), "M", true);
        addFiles(items, status.getRemoved(), "D", true);
        return items;
    }

    /**
     * Scans the working area to locate un-indexed alterations, modifications, untracked components, or deletions.
     */
    public List<GitFileItem> getUnstagedFiles() throws Exception {
        Status status = git.status().call();
        List<GitFileItem> items = new ArrayList<>();
        addFiles(items, status.getModified(), "M", false);
        addFiles(items, status.getUntracked(), "?", false);
        addFiles(items, status.getMissing(), "D", false);
        return items;
    }

    /**
     * Helper utility populating item arrays, applying filters to keep workspace structure
     * text documents out of change visibility matrices.
     */
    private void addFiles(List<GitFileItem> list, Set<String> paths, String status, boolean staged) {
        for (String path : paths) {
            String fileName = new File(path).getName();
            if (fileName.equals("session.json") || fileName.equals("project_meta.json") || fileName.equals(".gitignore")) {
                continue;
            }
            list.add(new GitFileItem(path, fileName, status, staged));
        }
    }

    /**
     * Stages an individual file target into the index. If the file has been deleted locally,
     * dispatches removal tracking directives instead.
     */
    public void stageFile(String path) throws Exception {
        File file = new File(git.getRepository().getWorkTree(), path);
        if (!file.exists()) {
            git.rm().addFilepattern(path).call();
        } else {
            git.add().addFilepattern(path).call();
        }
    }

    /**
     * Removes an individual target file from the staged index area without altering its contents on disk.
     */
    public void unstageFile(String path) throws Exception {
        git.reset().addPath(path).call();
    }

    /**
     * Aggregates all modified and untracked changes across the current working tree directory
     * directly into the staged index layer.
     */
    public void stageAll() throws Exception {
        git.add().addFilepattern(".").call();
        git.add().setUpdate(true).addFilepattern(".").call();
    }

    /**
     * Wipes structural staging properties globally across the workspace, resetting the index tier entirely.
     */
    public void unstageAll() throws Exception {
        git.reset().call();
    }

    /**
     * Generates a line-by-line unified syntax patch text representation for a target file.
     * Evaluates boundaries depending on whether the asset lives inside staging or working sectors.
     */
    public String getFileDiff(String path, boolean staged) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(git.getRepository());

            AbstractTreeIterator oldTree;
            AbstractTreeIterator newTree;

            if (staged) {
                oldTree = getHeadTree();
                newTree = new DirCacheIterator(git.getRepository().readDirCache());
            } else {
                oldTree = new DirCacheIterator(git.getRepository().readDirCache());
                newTree = new FileTreeIterator(git.getRepository());
            }

            List<DiffEntry> entries = formatter.scan(oldTree, newTree);
            for (DiffEntry entry : entries) {
                if (entry.getNewPath().equals(path) || entry.getOldPath().equals(path)) {
                    formatter.format(entry);
                }
            }
        }
        return out.toString();
    }

    /**
     * Walks the tree nodes linked against a specific commit identifier hash to reconstruct its file delta status list.
     */
    public List<GitFileItem> getFilesInCommit(String commitSha) throws Exception {
        List<GitFileItem> items = new ArrayList<>();
        Repository repository = git.getRepository();
        ObjectId commitId = repository.resolve(commitSha);

        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            RevCommit parent = commit.getParentCount() > 0 ? walk.parseCommit(commit.getParent(0).getId()) : null;

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 DiffFormatter df = new DiffFormatter(baos)) {
                df.setRepository(repository);
                List<DiffEntry> diffs;

                if (parent != null) {
                    diffs = df.scan(parent.getTree(), commit.getTree());
                } else {
                    EmptyTreeIterator emptyTree = new EmptyTreeIterator();
                    diffs = df.scan(emptyTree, new CanonicalTreeParser(null, walk.getObjectReader(), commit.getTree()));
                }

                for (DiffEntry diff : diffs) {
                    String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                    String name = new File(path).getName();

                    String status = "M";
                    if (diff.getChangeType() == DiffEntry.ChangeType.ADD) status = "A";
                    else if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) status = "D";

                    items.add(new GitFileItem(path, name, status, false));
                }
            }
        }
        return items;
    }

    /**
     * Extracts unified string patches representing modifications applied across a single file asset within an archived commit.
     */
    public String getCommitFileDiff(String commitSha, String path) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Repository repository = git.getRepository();
        ObjectId commitId = repository.resolve(commitSha);

        try (org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(repository);
             DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(repository);
            RevCommit commit = walk.parseCommit(commitId);
            RevCommit parent = commit.getParentCount() > 0 ? walk.parseCommit(commit.getParent(0).getId()) : null;

            List<DiffEntry> diffs;
            if (parent != null) {
                diffs = df.scan(parent.getTree(), commit.getTree());
            } else {
                EmptyTreeIterator emptyTree = new EmptyTreeIterator();
                diffs = df.scan(emptyTree, new CanonicalTreeParser(null, walk.getObjectReader(), commit.getTree()));
            }

            for (DiffEntry entry : diffs) {
                if (entry.getNewPath().equals(path) || entry.getOldPath().equals(path)) {
                    df.format(entry);
                }
            }
        }
        return out.toString();
    }

    /**
     * Binds a standard text message row to create a commit transaction using global profile markers.
     */
    public void commit(String message) throws Exception {
        git.commit().setMessage(message).call();
    }

    /**
     * Overloaded commit processor explicitly anchoring custom profile identity strings to authored changes fields.
     */
    public void commit(String message, String authorName, String authorEmail) throws Exception {
        git.commit()
                .setMessage(message)
                .setAuthor(authorName, authorEmail)
                .setCommitter(authorName, authorEmail)
                .call();
    }

    /**
     * Combines currently staged updates directly into the previous commit tip block, modifying its message field.
     */
    public void amendCommit(String message) throws Exception {
        git.commit().setAmend(true).setMessage(message).call();
    }

    /**
     * Traverses head references to compile chronological logs histories list sheets.
     * Gracefully exits with blank results vectors if the repository is completely fresh and uncommitted.
     */
    public List<CommitItem> getCommitHistory() throws Exception {
        List<CommitItem> items = new ArrayList<>();
        try {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                items.add(new CommitItem(
                        commit.getName(),
                        commit.abbreviate(7).name(),
                        commit.getFullMessage(),
                        commit.getAuthorIdent().getName(),
                        DateUtils.formatDate(commit.getAuthorIdent().getWhen())
                ));
            }
        } catch (org.eclipse.jgit.api.errors.NoHeadException e) {
            // Freshly initialized repositories have 0 commits.
        }
        return items;
    }

    /**
     * Resolves the string identifier name describing the currently checked out branch head context.
     */
    public String getCurrentBranchName() {
        try {
            if (git != null && git.getRepository() != null) {
                return git.getRepository().getBranch();
            }
        } catch (Exception ignored) {
        }
        return configuredDefaultBranch;
    }

    /**
     * Pulls the string remote access web location parameter linked behind default 'origin' profiles trackers.
     */
    public String getRemoteUrl() {
        try {
            if (git != null && git.getRepository() != null) {
                return git.getRepository().getConfig().getString("remote", "origin", "url");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * Commits configuration alterations directly to the underlying system config properties.
     * Wipes remote references cleanly if passed a null or blank string input payload.
     */
    public void setRemoteUrl(String url) throws Exception {
        if (git != null && git.getRepository() != null) {
            StoredConfig config = git.getRepository().getConfig();
            if (url == null || url.trim().isEmpty()) {
                config.unset("remote", "origin", "url");
                config.unset("remote", "origin", "fetch");
            } else {
                config.setString("remote", "origin", "url", url.trim());
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            }
            config.save();
        }
    }

    /**
     * Evaluates references maps definitions parameters to fetch lists cataloging working branches nodes.
     * @param remote True if the command layout loop should scan external remote tracking streams exclusively.
     */
    public List<BranchItem> getBranches(boolean remote) throws Exception {
        ListBranchCommand command = git.branchList();
        if (remote) {
            command.setListMode(ListBranchCommand.ListMode.REMOTE);
        }

        List<Ref> refs = command.call();
        List<BranchItem> items = new ArrayList<>();
        String currentBranch = git.getRepository().getFullBranch();

        for (Ref ref : refs) {
            boolean active = ref.getName().equals(currentBranch);
            items.add(new BranchItem(
                    Repository.shortenRefName(ref.getName()),
                    active,
                    ref.getName().startsWith("refs/remotes/"),
                    ref.getObjectId() != null ? ref.getObjectId().abbreviate(7).name() : ""
            ));
        }
        return items;
    }

    /**
     * Shifts active branch context scopes maps to match chosen local destination names markers.
     */
    public void checkoutBranch(String name) throws Exception {
        git.checkout().setName(name).call();
    }

    /**
     * Forks a brand new branch pointer pointing from an explicit historical start reference location.
     */
    public void createBranch(String name, String from) throws Exception {
        git.branchCreate().setName(name).setStartPoint(from).call();
    }

    /**
     * Forces the permanent destruction of chosen branch entries pointers out of local references sets arrays.
     */
    public void deleteBranch(String name) throws Exception {
        git.branchDelete().setBranchNames(name).setForce(true).call();
    }

    /**
     * Synthetically replicates alterations introduced by an external target commit, re-applying them onto the current tip row.
     */
    public void cherryPick(String commitSha) throws Exception {
        ObjectId commitId = git.getRepository().resolve(commitSha);
        if (commitId == null) throw new IllegalArgumentException("Commit SHA not found");
        git.cherryPick().include(commitId).call();
    }

    /**
     * Generates a structural rollback inversion commit designed to cleanly undo previous commit modifications changes vectors.
     */
    public void revertCommit(String commitSha, String authorName, String authorEmail) throws Exception {
        ObjectId commitId = git.getRepository().resolve(commitSha);
        if (commitId == null) throw new IllegalArgumentException("Commit SHA not found");

        StoredConfig config = git.getRepository().getConfig();
        config.setString("user", null, "name", authorName);
        config.setString("user", null, "email", authorEmail);
        config.save();

        git.revert()
                .include(commitId)
                .call();
    }

    /**
     * Merges structural modifications streams running out from separate branches sources into the active workspace focus track.
     */
    public void mergeBranch(String branchName) throws Exception {
        ObjectId branchId = git.getRepository().resolve(branchName);
        if (branchId == null) throw new IllegalArgumentException("Branch reference not found");
        git.merge().include(branchId).call();
    }

    /**
     * Modifies name references records tags assigned across existing local branches targets.
     */
    public void renameBranch(String oldName, String newName) throws Exception {
        git.branchRename().setOldName(oldName).setNewName(newName).call();
    }

    /**
     * Dispatches localized branch modification revisions forward to external hosting platforms channels repositories.
     */
    public void push(String remoteUrl, String pat, String branch) throws Exception {
        git.push()
                .setRemote(remoteUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(pat, ""))
                .add(branch)
                .call();
    }

    /**
     * Executes soft rollback reset sequences, moving reference tips while keeping staging indexes completely unharmed.
     */
    public void softReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(commitRef).call();
    }

    /**
     * Executes mixed rollback reset sequences, moving reference tips, clearing index items, while preserving local disk text fields lines.
     */
    public void mixedReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.MIXED).setRef(commitRef).call();
    }

    /**
     * Executes hard destructive reset sequences, rolling branch states back while fully wiping all unstaged local file amendments completely.
     */
    public void hardReset(String commitRef) throws Exception {
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(commitRef).call();
    }

    /**
     * Compiles a structural canonical parser mapping against the absolute top peak snapshot state node vector.
     */
    private AbstractTreeIterator getHeadTree() throws Exception {
        ObjectId head = git.getRepository().resolve("HEAD^{tree}");
        if (head == null) return new CanonicalTreeParser();
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = git.getRepository().newObjectReader()) {
            treeParser.reset(reader, head);
        }
        return treeParser;
    }
}
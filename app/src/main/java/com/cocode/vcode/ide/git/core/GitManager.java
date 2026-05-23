package com.cocode.vcode.ide.git.core;

import com.cocode.vcode.ide.git.model.CommitInfo;
import com.cocode.vcode.ide.git.model.FileStatus;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Core interface orchestrating version control actions via the JGit library framework.
 * Coordinates local workspace initializations, credentials provisioning,
 * commits generation, and processes history tree revisions.
 */
public class GitManager {

    // Default configuration entries applied across newly generated tracking exclusions files
    private static final String DEFAULT_GITIGNORE =
            "node_modules/\n.DS_Store\n*.log\ndist/\nbuild/\n.env\n.env.local\n*.class\n*.jar\nsession.json\nproject_meta.json";
    private final File workDir;
    private Git git;

    /**
     * Initializes the manager instance bound to a specified project working directory path.
     */
    public GitManager(File workDir) {
        this.workDir = workDir;
    }

    /**
     * Clones an external source code repository into a target local destination folder.
     * Hooks into progress monitors to deliver tracking updates back to UI listeners.
     * @param url       The absolute web URL pointing to the remote repository source.
     * @param targetDir The destination file directory path on the local file system.
     * @param username  The account username or organization profile handle identifier.
     * @param token     The authenticated personal access token used for validation.
     * @param callback  An active interface monitor tracking asynchronous download states.
     * @return A descriptive GitOperationResult wrapping process status signals.
     */
    public static GitOperationResult cloneRepo(String url, File targetDir,
                                               String username, String token,
                                               CloneProgressCallback callback) {
        try {
            org.eclipse.jgit.api.CloneCommand clone = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(targetDir)
                    .setCredentialsProvider(
                            new UsernamePasswordCredentialsProvider(
                                    username != null ? username : "token",
                                    token != null ? token : ""))
                    .setProgressMonitor(new org.eclipse.jgit.lib.ProgressMonitor() {

                        @Override
                        public void start(int totalTasks) {
                        }

                        @Override
                        public void beginTask(String title, int total) {
                            if (callback != null) callback.onProgress(title, 0, total);
                        }

                        @Override
                        public void update(int completed) {
                            if (callback != null) callback.onUpdate(completed);
                        }

                        @Override
                        public void endTask() {
                            if (callback != null) callback.onTaskDone();
                        }

                        @Override
                        public boolean isCancelled() {
                            return false;
                        }

                        @Override
                        public void showDuration(boolean enabled) {
                        }
                    });
            Git result = clone.call();
            result.close(); // Clean up system descriptors immediately after completion
            return GitOperationResult.success("Repository cloned successfully");
        } catch (GitAPIException e) {
            return GitOperationResult.error("Clone failed: " + e.getMessage());
        }
    }

    // --- INIT / OPEN ---

    /**
     * Verifies if a hidden configuration tracker metadata folder sits within the target root directory.
     */
    public boolean isGitRepo() {
        return workDir != null && new File(workDir, ".git").exists();
    }

    /**
     * Standard baseline repository entry initialization strategy falling back onto default master branch targets.
     */
    public GitOperationResult init() {
        return init("master");
    }

    /**
     * Initializes a fresh repository, explicitly applying chosen primary development tracking branches.
     */
    public GitOperationResult init(String defaultBranch) {
        try {
            org.eclipse.jgit.api.InitCommand initCommand = Git.init().setDirectory(workDir);
            if (defaultBranch != null && !defaultBranch.trim().isEmpty()) {
                initCommand.setInitialBranch(defaultBranch.trim());
            }
            git = initCommand.call();
            createDefaultGitIgnore(); // Inject standard project exclusion scripts automatically
            return GitOperationResult.success("Repository initialized");
        } catch (GitAPIException e) {
            return GitOperationResult.error("Init failed: " + e.getMessage());
        }
    }

    /**
     * Attaches JGit processing handles to an existing pre-configured local repository path.
     */
    public GitOperationResult open() {
        try {
            git = Git.open(workDir);
            return GitOperationResult.success("Repository opened");
        } catch (IOException e) {
            return GitOperationResult.error("Not a git repository: " + e.getMessage());
        }
    }

    // --- STATUS ---

    /**
     * Builds a localized list of ignored files templates if no pre-existing profiles are detected.
     */
    private void createDefaultGitIgnore() {
        File gitignore = new File(workDir, ".gitignore");
        if (!gitignore.exists()) {
            try {
                com.cocode.vcode.ide.utils.FileUtils.writeFile(gitignore, DEFAULT_GITIGNORE);
            } catch (IOException ignored) {
                // Non-blocking initialization fallback parameters parameters
            }
        }
    }

    // --- COMMITTING ---

    /**
     * Commits staged project updates to the tracking branch history sequence.
     * Maps author data configurations, defaulting to placeholder details if fields are unpopulated.
     */
    public GitOperationResult commit(String message, String authorName, String authorEmail) {
        if (checkGit()) return notInitialized();
        if (message == null || message.trim().isEmpty()) {
            return GitOperationResult.error("Commit message cannot be empty");
        }

        String name = authorName != null && !authorName.isEmpty() ? authorName : "Developer";
        String email = authorEmail != null && !authorEmail.isEmpty() ? authorEmail : name + "@webforge.local";

        try {
            RevCommit commit = git.commit()
                    .setMessage(message.trim())
                    .setAuthor(name, email)
                    .setCommitter(name, email)
                    .call();
            return GitOperationResult.success("Committed: " + commit.abbreviate(7).name());
        } catch (GitAPIException e) {
            return GitOperationResult.error("Commit failed: " + e.getMessage());
        }
    }

    // --- HISTORY ---

    /**
     * Resolves the revision history map data tracks, providing pagination limits controls.
     * @param maxCount Upper threshold bound representing total history cards requested.
     * @param skip     The baseline offset index boundary used to advance history pages views.
     * @return A compiled sequence tracking structured historical revision info profiles.
     */
    public List<CommitInfo> getCommitLog(int maxCount, int skip) {
        if (checkGit()) return new ArrayList<>();
        List<CommitInfo> logs = new ArrayList<>();
        try {
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().setMaxCount(maxCount).setSkip(skip).call();

            try (org.eclipse.jgit.revwalk.RevWalk rw = new org.eclipse.jgit.revwalk.RevWalk(git.getRepository())) {
                for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {

                    // Accumulate tracking parent node indices mappings sequences arrays
                    String[] parents = new String[commit.getParentCount()];
                    for (int i = 0; i < commit.getParentCount(); i++) {
                        parents[i] = commit.getParent(i).getName();
                    }

                    CommitInfo info = new CommitInfo(
                            commit.getName(),
                            commit.getName().substring(0, 7),
                            commit.getFullMessage(),
                            commit.getShortMessage(),
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent().getEmailAddress(),
                            commit.getAuthorIdent().getWhen(),
                            parents
                    );

                    // Reconstruct tree files state lists transitions related to this commit branch index
                    info.setChangedFiles(getChangedFilesInCommit(commit, rw));
                    logs.add(info);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logs;
    }

    /**
     * Compares tree data frames layout configurations to isolate file modifications records.
     */
    private List<FileStatus> getChangedFilesInCommit(org.eclipse.jgit.revwalk.RevCommit commit, org.eclipse.jgit.revwalk.RevWalk rw) {
        List<FileStatus> changedFiles = new ArrayList<>();
        try {
            Repository repo = git.getRepository();

            // Scenario A: First baseline root snapshot (No ancestral tracking trees exist yet)
            if (commit.getParentCount() == 0) {
                try (org.eclipse.jgit.treewalk.TreeWalk tw = new org.eclipse.jgit.treewalk.TreeWalk(repo)) {
                    tw.addTree(commit.getTree());
                    tw.setRecursive(true);
                    while (tw.next()) {
                        changedFiles.add(new FileStatus(tw.getPathString(), FileStatus.Type.STAGED_ADDED));
                    }
                }
            } else {
                // Scenario B: Standard node traversal verification mapping against parent frames elements
                org.eclipse.jgit.revwalk.RevCommit parent = rw.parseCommit(commit.getParent(0).getId());

                try (DiffFormatter df = new DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                    df.setRepository(repo);
                    df.setDiffComparator(org.eclipse.jgit.diff.RawTextComparator.DEFAULT);
                    df.setDetectRenames(true);

                    List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                    for (DiffEntry diff : diffs) {
                        FileStatus.Type type = FileStatus.Type.STAGED_MODIFIED;
                        String path = diff.getNewPath();

                        switch (diff.getChangeType()) {
                            case ADD:
                                type = FileStatus.Type.STAGED_ADDED;
                                break;
                            case DELETE:
                                type = FileStatus.Type.STAGED_DELETED;
                                path = diff.getOldPath();
                                break;
                            case RENAME:
                            case COPY:
                            case MODIFY:
                                break;
                        }
                        changedFiles.add(new FileStatus(path, type));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return changedFiles;
    }

    // --- HELPERS ---

    private boolean checkGit() {
        return git == null;
    }

    private GitOperationResult notInitialized() {
        return GitOperationResult.error("Git repository not initialized. Run git init first.");
    }

    /**
     * Disconnects repository resources and handles clean closing operations flags.
     */
    public void close() {
        if (git != null) {
            git.close();
            git = null;
        }
    }

    /**
     * Interface contract providing state synchronization coordinates updates for repository clone tasks.
     */
    public interface CloneProgressCallback {
        void onProgress(String task, int done, int total);

        void onUpdate(int completed);

        void onTaskDone();
    }
}
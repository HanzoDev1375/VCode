package com.cocode.vcode.ide.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Integrity preservation coordinator guarding project setup states.
 * Scans directories to confirm standard tracking logs remain present, auto-generating
 * default metadata records if files are missing or broken.
 */
public class ProjectFileRecovery {

    /**
     * Reviews state settings layout integrity, re-instantiating metadata sheets if they have been dropped.
     * Only runs for directories that VCode actually owns (inside VCodeProjects/).
     * External directories (Downloads, Documents, etc.) are skipped intentionally to avoid
     * creating stray metadata files in folders VCode doesn't own.
     */
    public static void ensureProjectFilesExist(File projectRoot) {
        if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
            return;
        }

        // Only create metadata files inside VCode-owned project directories
        String absPath = projectRoot.getAbsolutePath();
        if (!absPath.contains("/VCodeProjects/") && !absPath.endsWith("/VCodeProjects")) {
            return;
        }

        File metaFile = new File(projectRoot, "project_meta.json");
        if (!metaFile.exists()) {
            createDefaultProjectMeta(metaFile);
        }

        File sessionFile = new File(projectRoot, "session.json");
        if (!sessionFile.exists()) {
            createDefaultSession(sessionFile);
        }
    }

    /**
     * Restores default configuration metrics descriptors for project metadata fields.
     */
    private static void createDefaultProjectMeta(File metaFile) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("projectName", "VCode Project");
            meta.put("createdAt", System.currentTimeMillis());
            meta.put("version", "1.0.0");

            writeJsonToFile(metaFile, meta);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Instantiates blank workspace track configurations maps for newly restored environment nodes.
     */
    private static void createDefaultSession(File sessionFile) {
        try {
            JSONObject session = new JSONObject();
            session.put("openFiles", new JSONArray());
            session.put("activeTabIndex", -1);
            session.put("lastOpened", System.currentTimeMillis());

            writeJsonToFile(sessionFile, session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Serializes parameters configurations directly onto disk storage tracks using a clean 4-space layout.
     */
    private static void writeJsonToFile(File file, JSONObject json) throws IOException, JSONException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString(4));
            writer.flush();
        }
    }
}
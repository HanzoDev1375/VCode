package com.cocode.vcode.ide.git.model;

/**
 * Enumeration mapping core version control transaction identifiers commands flags.
 * Used by repository dashboard views controllers to dispatch chosen automation tasks strategies parameters.
 */
public enum GitAction {
    SOFT_RESET,    // Moves branch tracking pointer backwards while keeping staging text blocks perfectly intact
    HARD_RESET,    // Moves branch tracking pointer backwards while completely erasing local modifications records from disk
    MIXED_RESET,   // Moves branch tracking pointer backwards, preserving local files while resetting index blocks paths
    CHERRY_PICK,   // Extracts individual target commit changes to inject them directly onto current branch branches lines
    REVERT_COMMIT, // Autographs tracking inversion changes entries to cancel previously submitted commit effects parameters
    STASH,         // Shelves working alterations states down into temp cache files structures to clean the staging workspace
    STASH_POP      // Unpacks compiled shelved changes models entries back into active workspace paths files layouts layers
}
package com.cocode.vcode.ide.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import androidx.appcompat.app.AlertDialog;
import com.cocode.vcode.ide.data.model.SnippetItem;
import com.cocode.vcode.ide.databinding.DialogCommitTemplatesBinding;
import com.cocode.vcode.ide.ui.snippets.SnippetsAdapter;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

public class CommitTemplatesDialog {

    public interface CommitTemplateListener {
        void onTemplateSelected(SnippetItem template);
        void onSaveCurrentMessage();
        void onTemplateEdit(SnippetItem template);
        void onTemplateDelete(SnippetItem template);
    }

    public static void show(Context context, List<SnippetItem> templates, CommitTemplateListener listener) {
        DialogCommitTemplatesBinding binding = DialogCommitTemplatesBinding.inflate(LayoutInflater.from(context));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setCancelable(true)
                .create();

        // Apply fonts
        binding.tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.tvDesc.setTypeface(FontManager.getInstance().getUiMedium(context));
        binding.btnCancel.setTypeface(FontManager.getInstance().getUiSemiBold(context));
        binding.btnSaveTemplate.setTypeface(FontManager.getInstance().getUiSemiBold(context));

        // Setup RecyclerView
        SnippetsAdapter adapter = new SnippetsAdapter(new SnippetsAdapter.SnippetListener() {
            @Override
            public void onSnippetClick(SnippetItem snippet) {
                if (listener != null) listener.onTemplateSelected(snippet);
                dialog.dismiss();
            }

            @Override
            public void onSnippetEditClick(SnippetItem snippet) {
                if (listener != null) listener.onTemplateEdit(snippet);
                dialog.dismiss();
            }

            @Override
            public void onSnippetDeleteClick(SnippetItem snippet) {
                if (listener != null) listener.onTemplateDelete(snippet);
            }
        });
        
        adapter.setSnippets(templates);
        binding.rvTemplates.setAdapter(adapter);

        binding.btnCancel.setOnClickListener(v -> dialog.dismiss());

        binding.btnSaveTemplate.setOnClickListener(v -> {
            if (listener != null) listener.onSaveCurrentMessage();
            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
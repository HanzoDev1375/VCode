package com.cocode.vcode.ide.ui.sheets;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.databinding.VcodeBottomSheetTodoBinding;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodoPanelBottomSheet extends BottomSheetDialogFragment {

    /** Tags to scan for, in priority order. */
    private static final Pattern PAT_TODO = Pattern.compile(
            "(?://|#|/\\*|\\*)\\s*(TODO|FIXME|HACK|NOTE|XXX)\\s*:?\\s*(.*)",
            Pattern.CASE_INSENSITIVE);

    public interface TodoListener {
        void onTodoSelected(File file, int lineNumber);
    }

    private File projectRoot;
    private TodoListener listener;
    private VcodeBottomSheetTodoBinding binding;

    public static TodoPanelBottomSheet newInstance(File projectRoot) {
        TodoPanelBottomSheet sheet = new TodoPanelBottomSheet();
        sheet.projectRoot = projectRoot;
        return sheet;
    }

    public void setListener(TodoListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = VcodeBottomSheetTodoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FontManager fm = FontManager.getInstance();
        binding.tvTitle.setTypeface(fm.getUiSemiBold(requireContext()));

        binding.rvTodos.setLayoutManager(new LinearLayoutManager(requireContext()));
        TodoAdapter adapter = new TodoAdapter();
        binding.rvTodos.setAdapter(adapter);

        scan(adapter);
    }

    /** Refresh from latest saved files — called when a file is saved. */
    public void refresh() {
        if (binding == null || !isAdded()) return;
        TodoAdapter adapter = (TodoAdapter) binding.rvTodos.getAdapter();
        if (adapter != null) scan(adapter);
    }

    private void scan(TodoAdapter adapter) {
        if (projectRoot == null) return;
        binding.progressTodo.setVisibility(View.VISIBLE);

        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<TodoItem> items = new ArrayList<>();
            scanDir(projectRoot, items);
            ExecutorProvider.getInstance().runOnMain(() -> {
                if (binding == null) return;
                binding.progressTodo.setVisibility(View.GONE);
                adapter.setItems(items);
                binding.tvCount.setText(String.valueOf(items.size()));
                binding.layoutEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                binding.rvTodos.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void scanDir(File dir, List<TodoItem> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("build")) continue;
            if (f.isDirectory()) {
                scanDir(f, out);
            } else if (isTextFile(name) && f.length() < 512 * 1024) {
                scanFile(f, out);
            }
        }
    }

    private void scanFile(File file, List<TodoItem> out) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                Matcher m = PAT_TODO.matcher(line);
                if (m.find()) {
                    String tag  = m.group(1).toUpperCase();
                    String text = m.group(2) != null ? m.group(2).trim() : "";
                    out.add(new TodoItem(file, lineNum, tag, text));
                }
            }
        } catch (Exception ignored) {}
    }

    private boolean isTextFile(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".js") || n.endsWith(".ts") || n.endsWith(".jsx") || n.endsWith(".tsx")
            || n.endsWith(".html") || n.endsWith(".css") || n.endsWith(".scss") || n.endsWith(".json")
            || n.endsWith(".md") || n.endsWith(".txt") || n.endsWith(".xml") || n.endsWith(".java")
            || n.endsWith(".py") || n.endsWith(".sh") || n.endsWith(".yml") || n.endsWith(".yaml");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─── Model ───────────────────────────────────────────────────────────────

    static class TodoItem {
        final File file;
        final int line;
        final String tag;   // TODO / FIXME / HACK / NOTE
        final String text;

        TodoItem(File file, int line, String tag, String text) {
            this.file = file; this.line = line; this.tag = tag; this.text = text;
        }
    }

    // ─── Adapter ─────────────────────────────────────────────────────────────

    private class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.VH> {
        private List<TodoItem> items = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void setItems(List<TodoItem> newItems) {
            items = newItems;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            com.cocode.vcode.ide.databinding.VcodeItemTodoBinding b =
                    com.cocode.vcode.ide.databinding.VcodeItemTodoBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TodoItem item = items.get(position);
            FontManager fm = FontManager.getInstance();

            holder.b.tvTag.setText(item.tag);
            holder.b.tvTag.setTypeface(fm.getUiSemiBold(holder.itemView.getContext()));

            // Tag badge color
            int tagColor;
            switch (item.tag) {
                case "FIXME": tagColor = 0xFFD93B38; break;
                case "HACK":  tagColor = 0xFFD4850A; break;
                case "NOTE":  tagColor = 0xFF2B6EE8; break;
                default:      tagColor = 0xFF1FB870; break; // TODO
            }
            holder.b.tvTag.setTextColor(tagColor);

            holder.b.tvText.setText(item.text.isEmpty() ? "(no description)" : item.text);
            holder.b.tvText.setTypeface(fm.getUiMedium(holder.itemView.getContext()));

            String relPath = (projectRoot != null)
                    ? item.file.getAbsolutePath().replace(projectRoot.getAbsolutePath() + File.separator, "")
                    : item.file.getName();
            holder.b.tvLocation.setText(relPath + ":" + item.line);
            holder.b.tvLocation.setTypeface(fm.getCodeFont(holder.itemView.getContext()));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTodoSelected(item.file, item.line);
                dismiss();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final com.cocode.vcode.ide.databinding.VcodeItemTodoBinding b;
            VH(com.cocode.vcode.ide.databinding.VcodeItemTodoBinding b) {
                super(b.getRoot()); this.b = b;
            }
        }
    }
}

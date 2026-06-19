package com.cocode.vcode.ide.ui.sheets;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.search.SearchEngine;
import com.cocode.vcode.ide.core.search.SearchResult;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import android.widget.EditText;
import com.cocode.vcode.ide.databinding.VcodeBottomSheetProjectSearchBinding;
import java.util.ArrayList;
import java.util.List;

public class ProjectSearchBottomSheet extends BottomSheetDialogFragment {

    private File projectRoot;
    private SearchEngine searchEngine;
    private SearchAdapter adapter;
    private ProjectSearchListener listener;
    
    private VcodeBottomSheetProjectSearchBinding binding;
    
    private Runnable pendingSearch;

    public interface ProjectSearchListener {
        void onSearchResultSelected(File file, int lineNumber);
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
    }

    public void setListener(ProjectSearchListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = VcodeBottomSheetProjectSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchEngine = new SearchEngine();

        UiUtils.setViewRounded(binding.etSearchQuery, UiUtils.dpToPx(requireContext(), 10), androidx.core.content.ContextCompat.getColor(requireContext(), R.color.vcode_bg_elevated));
        binding.etSearchQuery.setTypeface(FontManager.getInstance().getUiMedium(requireContext()));
        
        if (binding.tvTitle != null) {
            binding.tvTitle.setTypeface(FontManager.getInstance().getUiSemiBold(requireContext()));
        }

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchAdapter();
        binding.rvSearchResults.setAdapter(adapter);

        binding.etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    binding.etSearchQuery.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> performSearch(s.toString());
                binding.etSearchQuery.postDelayed(pendingSearch, 300);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty() || projectRoot == null) {
            adapter.setResults(new ArrayList<>());
            return;
        }

        binding.progressSearch.setVisibility(View.VISIBLE);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<ProjectSearchResult> allResults = new ArrayList<>();
            searchInDirectory(projectRoot, query, allResults);

            ExecutorProvider.getInstance().runOnMain(() -> {
                binding.progressSearch.setVisibility(View.INVISIBLE);
                adapter.setResults(allResults);
            });
        });
    }

    private void searchInDirectory(File dir, String query, List<ProjectSearchResult> outResults) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            String name = f.getName().toLowerCase();
            // Directory exclusions
            if (name.equals(".git") || name.equals("node_modules") || name.equals(".idea") || name.equals("build")) continue;

            if (f.isDirectory()) {
                searchInDirectory(f, query, outResults);
            } else {
                // File exclusions
                if (name.equals("project_meta.json") || name.equals("session.json") || name.equals("snippets.json")) continue;
                
                // Binary and image exclusions
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") ||
                    name.endsWith(".ico") || name.endsWith(".ttf") || name.endsWith(".woff") ||
                    name.endsWith(".woff2") || name.endsWith(".eot") || name.endsWith(".pdf") ||
                    name.endsWith(".mp3") || name.endsWith(".mp4") || name.endsWith(".wav") ||
                    name.endsWith(".ogg") || name.endsWith(".zip") || name.endsWith(".tar") ||
                    name.endsWith(".gz") || name.endsWith(".apk") || name.endsWith(".jar") ||
                    name.endsWith(".class") || name.endsWith(".dex")) {
                    continue;
                }

                try {
                    // Only read reasonably sized files, skip files > 500kb
                    if (f.length() > 1024 * 500) continue;

                    // Use BufferedReader for API 23 compatibility (Files.readAllBytes requires API 26)
                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader br = new java.io.BufferedReader(
                            new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
                        char[] buf = new char[4096];
                        int read;
                        while ((read = br.read(buf)) != -1) sb.append(buf, 0, read);
                    }
                    String content = sb.toString();
                    List<SearchResult> results = searchEngine.find(query, content, false, false, false);
                    for (SearchResult r : results) {
                        int start = Math.max(0, r.absoluteStart - 20);
                        int end = Math.min(content.length(), r.absoluteEnd + 40);
                        String snippet = content.substring(start, end).replace('\n', ' ').trim();
                        outResults.add(new ProjectSearchResult(f, r.lineNumber, snippet));
                        if (outResults.size() > 200) return; // limit
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private class ProjectSearchResult {
        File file;
        int line;
        String snippet;

        ProjectSearchResult(File file, int line, String snippet) {
            this.file = file;
            this.line = line;
            this.snippet = snippet;
        }
    }

    private class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {
        private List<ProjectSearchResult> items = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged")
        void setResults(List<ProjectSearchResult> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            com.cocode.vcode.ide.databinding.VcodeItemProjectSearchResultBinding itemBinding = 
                com.cocode.vcode.ide.databinding.VcodeItemProjectSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectSearchResult item = items.get(position);
            
            holder.binding.tvFileName.setText(item.file.getName());
            holder.binding.tvFileName.setTypeface(FontManager.getInstance().getUiSemiBold(holder.itemView.getContext()));
            
            com.cocode.vcode.ide.utils.FileIconHelper.setFileIconAndColor(holder.binding.ivFileIcon, item.file.getName());
            
            String relPath = item.file.getAbsolutePath().replace(projectRoot.getAbsolutePath() + File.separator, "");
            holder.binding.tvFilePath.setText(relPath);
            holder.binding.tvFilePath.setTypeface(FontManager.getInstance().getUiMedium(holder.itemView.getContext()));
            
            holder.binding.tvLineNumber.setText(item.line + ":");
            holder.binding.tvLineNumber.setTypeface(FontManager.getInstance().getCodeFont(holder.itemView.getContext()));
            
            holder.binding.tvSnippet.setText(item.snippet);
            holder.binding.tvSnippet.setTypeface(FontManager.getInstance().getCodeFont(holder.itemView.getContext()));

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSearchResultSelected(item.file, item.line);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            com.cocode.vcode.ide.databinding.VcodeItemProjectSearchResultBinding binding;

            ViewHolder(@NonNull com.cocode.vcode.ide.databinding.VcodeItemProjectSearchResultBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}

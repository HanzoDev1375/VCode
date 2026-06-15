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

import com.cocode.vcode.ide.R;
import com.cocode.vcode.ide.core.search.SearchEngine;
import com.cocode.vcode.ide.core.search.SearchResult;
import com.cocode.vcode.ide.utils.ExecutorProvider;
import com.cocode.vcode.ide.utils.FontManager;
import com.cocode.vcode.ide.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ProjectSearchBottomSheet extends BottomSheetDialogFragment {

    private File projectRoot;
    private SearchEngine searchEngine;
    private SearchAdapter adapter;
    private ProjectSearchListener listener;
    
    private TextInputEditText etSearchQuery;
    private LinearProgressIndicator progressSearch;
    private RecyclerView rvSearchResults;
    
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
        return inflater.inflate(R.layout.vcode_bottom_sheet_project_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        searchEngine = new SearchEngine();

        etSearchQuery = view.findViewById(R.id.et_search_query);
        progressSearch = view.findViewById(R.id.progress_search);
        rvSearchResults = view.findViewById(R.id.rv_search_results);

        rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SearchAdapter();
        rvSearchResults.setAdapter(adapter);

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    etSearchQuery.removeCallbacks(pendingSearch);
                }
                pendingSearch = () -> performSearch(s.toString());
                etSearchQuery.postDelayed(pendingSearch, 300);
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

        progressSearch.setVisibility(View.VISIBLE);
        ExecutorProvider.getInstance().runOnCpu(() -> {
            List<ProjectSearchResult> allResults = new ArrayList<>();
            searchInDirectory(projectRoot, query, allResults);

            ExecutorProvider.getInstance().runOnMain(() -> {
                progressSearch.setVisibility(View.INVISIBLE);
                adapter.setResults(allResults);
            });
        });
    }

    private void searchInDirectory(File dir, String query, List<ProjectSearchResult> outResults) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            // Very simple exclude
            if (f.getName().equals(".git") || f.getName().equals("node_modules")) continue;

            if (f.isDirectory()) {
                searchInDirectory(f, query, outResults);
            } else {
                try {
                    // Only read reasonably sized files, skip binaries implicitly or explicitly
                    if (f.length() > 1024 * 500) continue; // skip files > 500kb
                    
                    String content = new String(Files.readAllBytes(f.toPath()));
                    List<SearchResult> results = searchEngine.find(query, content, false, false, false);
                    for (SearchResult r : results) {
                        int start = Math.max(0, r.getStart() - 20);
                        int end = Math.min(content.length(), r.getEnd() + 40);
                        String snippet = content.substring(start, end).replace('\n', ' ').trim();
                        outResults.add(new ProjectSearchResult(f, r.getLine(), snippet));
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
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.vcode_item_project_search_result, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectSearchResult item = items.get(position);
            holder.tvFileName.setText(item.file.getName());
            
            String relPath = item.file.getAbsolutePath().replace(projectRoot.getAbsolutePath() + File.separator, "");
            holder.tvFilePath.setText(relPath);
            holder.tvLineNumber.setText(item.line + ":");
            holder.tvSnippet.setText(item.snippet);

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
            TextView tvFileName, tvFilePath, tvLineNumber, tvSnippet;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvFileName = itemView.findViewById(R.id.tv_file_name);
                tvFilePath = itemView.findViewById(R.id.tv_file_path);
                tvLineNumber = itemView.findViewById(R.id.tv_line_number);
                tvSnippet = itemView.findViewById(R.id.tv_snippet);
            }
        }
    }
}

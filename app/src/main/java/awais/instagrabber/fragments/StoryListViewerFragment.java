package awais.instagrabber.fragments;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import awais.instagrabber.R;
import awais.instagrabber.adapters.FeedStoriesListAdapter;
import awais.instagrabber.adapters.FeedStoriesListAdapter.OnFeedStoryClickListener;
import awais.instagrabber.adapters.HighlightStoriesListAdapter;
import awais.instagrabber.adapters.HighlightStoriesListAdapter.OnHighlightStoryClickListener;
import awais.instagrabber.customviews.helpers.RecyclerLazyLoader;
import awais.instagrabber.databinding.FragmentStoryListViewerBinding;
import awais.instagrabber.fragments.main.FeedFragment;
import awais.instagrabber.fragments.settings.MorePreferencesFragmentDirections;
import awais.instagrabber.models.FeedStoryModel;
import awais.instagrabber.models.HighlightModel;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.CookieUtils;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.viewmodels.FeedStoriesViewModel;
import awais.instagrabber.viewmodels.ArchivesViewModel;
import awais.instagrabber.webservices.ServiceCallback;
import awais.instagrabber.webservices.StoriesService;
import awais.instagrabber.webservices.StoriesService.ArchiveFetchResponse;

import static awais.instagrabber.utils.Utils.settingsHelper;

public final class StoryListViewerFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "StoryListViewerFragment";

    private AppCompatActivity fragmentActivity;
    private FragmentStoryListViewerBinding binding;
    private SwipeRefreshLayout root;
    private boolean shouldRefresh = true, firstRefresh = true;
    private FeedStoriesViewModel feedStoriesViewModel;
    private ArchivesViewModel archivesViewModel;
    private StoriesService storiesService;
    private Context context;
    private String type, endCursor = null;
    private RecyclerLazyLoader lazyLoader;

    private final OnFeedStoryClickListener clickListener = new OnFeedStoryClickListener() {
        @Override
        public void onFeedStoryClick(final FeedStoryModel model, final int position) {
            if (model == null) return;
            final NavDirections action = StoryListViewerFragmentDirections.actionStoryListFragmentToStoryViewerFragment(position, null, false, false, null, null, false);
            NavHostFragment.findNavController(StoryListViewerFragment.this).navigate(action);
        }

        @Override
        public void onProfileClick(final String username) {
            openProfile(username);
        }
    };

    private final OnHighlightStoryClickListener archiveClickListener = new OnHighlightStoryClickListener() {
        @Override
        public void onHighlightClick(final HighlightModel model, final int position) {
            if (model == null) return;
            final NavDirections action = StoryListViewerFragmentDirections.actionStoryListFragmentToStoryViewerFragment(
                    position, getString(R.string.action_archive), false, false, null, null, true);
            NavHostFragment.findNavController(StoryListViewerFragment.this).navigate(action);
        }

        @Override
        public void onProfileClick(final String username) {
            openProfile(username);
        }
    };

    private final ServiceCallback<ArchiveFetchResponse> cb = new ServiceCallback<ArchiveFetchResponse>() {
        @Override
        public void onSuccess(final ArchiveFetchResponse result) {
            endCursor = result.getNextCursor();
            final List<HighlightModel> models = archivesViewModel.getList().getValue();
            final List<HighlightModel> modelsCopy = models == null ? new ArrayList<>() : new ArrayList<>(models);
            modelsCopy.addAll(result.getResult());
            archivesViewModel.getList().postValue(modelsCopy);
            binding.swipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onFailure(final Throwable t) {
            Log.e(TAG, "Error", t);
            try {
                final Context context = getContext();
                Toast.makeText(context, t.getMessage(), Toast.LENGTH_SHORT).show();
            }
            catch (Exception e) {}
        }
    };

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentActivity = (AppCompatActivity) requireActivity();
        context = getContext();
        if (context == null) return;
        storiesService = StoriesService.getInstance();
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        if (root != null) {
            shouldRefresh = false;
            return root;
        }
        binding = FragmentStoryListViewerBinding.inflate(getLayoutInflater());
        root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        if (!shouldRefresh) return;
        init();
        shouldRefresh = false;
    }

    @Override
    public void onDestroy() {
        if (archivesViewModel != null) archivesViewModel.getList().postValue(null);
        super.onDestroy();
    }

    private void init() {
        final Context context = getContext();
        if (getArguments() == null) return;
        final StoryListViewerFragmentArgs fragmentArgs = StoryListViewerFragmentArgs.fromBundle(getArguments());
        type = fragmentArgs.getType();
        binding.swipeRefreshLayout.setOnRefreshListener(this);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        final ActionBar actionBar = fragmentActivity.getSupportActionBar();
        if (type == "feed") {
            if (actionBar != null) actionBar.setTitle(R.string.feed_stories);
            feedStoriesViewModel = new ViewModelProvider(this).get(FeedStoriesViewModel.class);
            final FeedStoriesListAdapter adapter = new FeedStoriesListAdapter(clickListener);
            binding.rvStories.setLayoutManager(layoutManager);
            binding.rvStories.setAdapter(adapter);
            feedStoriesViewModel.getList().observe(getViewLifecycleOwner(), adapter::submitList);
        }
        else {
            if (actionBar != null) actionBar.setTitle(R.string.action_archive);
            lazyLoader = new RecyclerLazyLoader(layoutManager, (page, totalItemsCount) -> {
                if (!TextUtils.isEmpty(endCursor)) onRefresh();
                endCursor = null;
            });
            binding.rvStories.addOnScrollListener(lazyLoader);
            archivesViewModel = new ViewModelProvider(fragmentActivity).get(ArchivesViewModel.class);
            final HighlightStoriesListAdapter adapter = new HighlightStoriesListAdapter(archiveClickListener);
            binding.rvStories.setLayoutManager(layoutManager);
            binding.rvStories.setAdapter(adapter);
            archivesViewModel.getList().observe(getViewLifecycleOwner(), adapter::submitList);
        }
        onRefresh();
    }

    @Override
    public void onRefresh() {
        binding.swipeRefreshLayout.setRefreshing(true);
        if (type == "feed" && firstRefresh) {
            binding.swipeRefreshLayout.setRefreshing(false);
            feedStoriesViewModel.getList().postValue(FeedFragment.feedStories);
            firstRefresh = false;
        }
        else if (type == "feed") {
            final String cookie = settingsHelper.getString(Constants.COOKIE);
            storiesService.getFeedStories(CookieUtils.getCsrfTokenFromCookie(cookie), new ServiceCallback<List<FeedStoryModel>>() {
                @Override
                public void onSuccess(final List<FeedStoryModel> result) {
                    feedStoriesViewModel.getList().postValue(result);
                    FeedFragment.feedStories = result;
                    binding.swipeRefreshLayout.setRefreshing(false);
                }

                @Override
                public void onFailure(final Throwable t) {
                    Log.e(TAG, "failed", t);
                    Toast.makeText(context, t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        else if (type == "archive") {
            storiesService.fetchArchive(endCursor, cb);
        }
    }

    private void openProfile(final String username) {
        final NavDirections action = MorePreferencesFragmentDirections
                .actionGlobalProfileFragment("@" + username);
        NavHostFragment.findNavController(this).navigate(action);
    }
}
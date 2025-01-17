package awais.instagrabber.webservices;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableMap;

import awais.instagrabber.repositories.SearchRepository;
import awais.instagrabber.repositories.responses.search.SearchResponse;
import awais.instagrabber.utils.TextUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class SearchService extends BaseService {
    private static final String TAG = "LocationService";

    private final SearchRepository repository;

    private static SearchService instance;

    private SearchService() {
        final Retrofit retrofit = getRetrofitBuilder()
                .baseUrl("https://www.instagram.com")
                .build();
        repository = retrofit.create(SearchRepository.class);
    }

    public static SearchService getInstance() {
        if (instance == null) {
            instance = new SearchService();
        }
        return instance;
    }

    public Call<SearchResponse> search(final boolean isLoggedIn,
                                       final String query,
                                       final String context) {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("query", query);
        // context is one of: "blended", "user", "place", "hashtag"
        // note that "place" and "hashtag" can contain ONE user result, who knows why
        builder.put("context", context);
        builder.put("count", "50");
        return repository.search(isLoggedIn
                ? "https://i.instagram.com/api/v1/fbsearch/topsearch_flat/"
                : "https://www.instagram.com/web/search/topsearch/",
                builder.build());
    }
}

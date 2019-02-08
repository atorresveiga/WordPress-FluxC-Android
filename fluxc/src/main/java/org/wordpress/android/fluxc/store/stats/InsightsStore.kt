package org.wordpress.android.fluxc.store.stats

import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.CacheMode
import org.wordpress.android.fluxc.model.stats.CommentsModel
import org.wordpress.android.fluxc.model.stats.FetchMode
import org.wordpress.android.fluxc.model.stats.FollowersModel
import org.wordpress.android.fluxc.model.stats.InsightsAllTimeModel
import org.wordpress.android.fluxc.model.stats.InsightsLatestPostModel
import org.wordpress.android.fluxc.model.stats.InsightsMapper
import org.wordpress.android.fluxc.model.stats.InsightsMostPopularModel
import org.wordpress.android.fluxc.model.stats.FetchMode.Paged
import org.wordpress.android.fluxc.model.stats.PublicizeModel
import org.wordpress.android.fluxc.model.stats.TagsModel
import org.wordpress.android.fluxc.model.stats.VisitsModel
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.FollowerType
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.FollowerType.EMAIL
import org.wordpress.android.fluxc.network.rest.wpcom.stats.InsightsRestClient.FollowerType.WP_COM
import org.wordpress.android.fluxc.network.utils.StatsGranularity.DAYS
import org.wordpress.android.fluxc.persistence.InsightsSqlUtils
import org.wordpress.android.fluxc.store.StatsStore.OnStatsFetched
import org.wordpress.android.fluxc.store.StatsStore.StatsError
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.INVALID_RESPONSE
import org.wordpress.android.fluxc.utils.CurrentTimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class InsightsStore
@Inject constructor(
    private val restClient: InsightsRestClient,
    private val sqlUtils: InsightsSqlUtils,
    private val insightsMapper: InsightsMapper,
    private val coroutineContext: CoroutineContext
) {

    // Publicize stats
    suspend fun fetchPublicizeData(siteModel: SiteModel, pageSize: Int, forced: Boolean = false) =
            withContext(coroutineContext) {
                val response = restClient.fetchPublicizeData(siteModel, pageSize = pageSize + 1, forced = forced)
                return@withContext when {
                    response.isError -> {
                        OnStatsFetched(response.error)
                    }
                    response.response != null -> {
                        sqlUtils.insert(siteModel, response.response)
                        OnStatsFetched(insightsMapper.map(response.response, pageSize))
                    }
                    else -> OnStatsFetched(StatsError(INVALID_RESPONSE))
                }
            }

    fun getPublicizeData(site: SiteModel, pageSize: Int): PublicizeModel? {
        return sqlUtils.selectPublicizeInsights(site)?.let { insightsMapper.map(it, pageSize) }
    }
}

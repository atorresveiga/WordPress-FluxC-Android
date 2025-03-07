package org.wordpress.android.fluxc.persistence.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.android.fluxc.domain.Addon
import org.wordpress.android.fluxc.domain.Addon.HasAdjustablePrice.Price.Adjusted.PriceType
import org.wordpress.android.fluxc.domain.GlobalAddonGroup
import org.wordpress.android.fluxc.persistence.WCAndroidDatabase
import org.wordpress.android.fluxc.persistence.entity.AddonEntity
import org.wordpress.android.fluxc.persistence.entity.AddonEntity.LocalPriceType
import org.wordpress.android.fluxc.persistence.entity.AddonEntity.LocalTitleFormat
import org.wordpress.android.fluxc.persistence.entity.AddonEntity.LocalType
import org.wordpress.android.fluxc.persistence.entity.AddonOptionEntity
import org.wordpress.android.fluxc.persistence.entity.AddonWithOptions
import org.wordpress.android.fluxc.persistence.entity.GlobalAddonGroupEntity
import org.wordpress.android.fluxc.persistence.entity.GlobalAddonGroupWithAddons

@RunWith(RobolectricTestRunner::class)
internal class AddonsDaoTest {
    private lateinit var database: WCAndroidDatabase
    private lateinit var sut: AddonsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, WCAndroidDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sut = database.addonsDao()
    }

    @Test
    fun `save and retrieve global add-on`(): Unit = runBlocking {
        val expectedGlobalAddonGroupEntity = getSampleGlobalAddonGroupWithAddons(DB_GENERATED_ID_IN_FIRST_ITERATION)

        sut.cacheGroups(
                globalAddonGroups = listOf(TEST_GLOBAL_ADDON_GROUP_DTO),
                siteRemoteId = TEST_SITE_REMOTE_ID
        )

        val resultFromDatabase = sut.observeGlobalAddonsForSite(TEST_SITE_REMOTE_ID).first()
        assertThat(resultFromDatabase).containsOnly(expectedGlobalAddonGroupEntity)
    }

    @Test
    fun `caching global addon groups doesn't duplicate entities`(): Unit = runBlocking {
        val expectedGlobalAddonGroupEntity = getSampleGlobalAddonGroupWithAddons(DB_GENERATED_ID_IN_SECOND_ITERATION)

        sut.cacheGroups(
                globalAddonGroups = listOf(TEST_GLOBAL_ADDON_GROUP_DTO),
                siteRemoteId = TEST_SITE_REMOTE_ID
        )
        sut.cacheGroups(
                globalAddonGroups = listOf(TEST_GLOBAL_ADDON_GROUP_DTO),
                siteRemoteId = TEST_SITE_REMOTE_ID
        )

        val resultFromDatabase = sut.observeGlobalAddonsForSite(TEST_SITE_REMOTE_ID).first()
        assertThat(resultFromDatabase).containsOnly(expectedGlobalAddonGroupEntity)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private companion object {
        const val DB_GENERATED_ID_IN_FIRST_ITERATION = 1L
        const val DB_GENERATED_ID_IN_SECOND_ITERATION = 2L
        const val TEST_SITE_REMOTE_ID = 5L

        fun getSampleGlobalAddonGroupWithAddons(autoGeneratedId: Long): GlobalAddonGroupWithAddons {
            return GlobalAddonGroupWithAddons(
                    group = GlobalAddonGroupEntity(
                            globalGroupLocalId = autoGeneratedId,
                            name = "Test Group",
                            restrictedCategoriesIds = emptyList(),
                            siteRemoteId = TEST_SITE_REMOTE_ID
                    ),
                    addons = listOf(
                            AddonWithOptions(
                                    addon = AddonEntity(
                                            addonLocalId = autoGeneratedId,
                                            globalGroupLocalId = autoGeneratedId,
                                            type = LocalType.Checkbox,
                                            name = "Test Addon name",
                                            titleFormat = LocalTitleFormat.Heading,
                                            description = "Test",
                                            required = false,
                                            position = 4
                                    ),
                                    options = listOf(
                                            AddonOptionEntity(
                                                    addonLocalId = autoGeneratedId,
                                                    addonOptionLocalId = autoGeneratedId,
                                                    priceType = LocalPriceType.FlatFee,
                                                    label = "Test label",
                                                    price = "Test price",
                                                    image = null
                                            )
                                    )
                            )
                    )

            )
        }

        val TEST_GLOBAL_ADDON_GROUP_DTO = GlobalAddonGroup(
                name = "Test Group",
                restrictedCategoriesIds = GlobalAddonGroup.CategoriesRestriction.AllProductsCategories,
                addons = listOf(
                        Addon.Checkbox(
                                titleFormat = Addon.TitleFormat.Heading,
                                description = "Test",
                                name = "Test Addon name",
                                required = false,
                                position = 4,
                                options = listOf(
                                        Addon.HasOptions.Option(
                                                price = Addon.HasAdjustablePrice.Price.Adjusted(
                                                        priceType = PriceType.FlatFee,
                                                        value = "Test price"
                                                ),
                                                label = "Test label",
                                                image = null
                                        )
                                )
                        )
                )
        )
    }
}

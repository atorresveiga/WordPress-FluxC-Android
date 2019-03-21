package org.wordpress.android.fluxc.wc.order

import com.yarolegovich.wellsql.WellSql
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.wordpress.android.fluxc.SingleStoreWellSqlConfigForTests
import org.wordpress.android.fluxc.UnitTestUtils
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCOrderModel
import org.wordpress.android.fluxc.model.WCOrderNoteModel
import org.wordpress.android.fluxc.model.WCOrderStatusModel
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus
import org.wordpress.android.fluxc.persistence.OrderSqlUtils
import org.wordpress.android.fluxc.persistence.WellSqlConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner::class)
class OrderSqlUtilsTest {
    @Before
    fun setUp() {
        val appContext = RuntimeEnvironment.application.applicationContext
        val config = SingleStoreWellSqlConfigForTests(
                appContext,
                listOf(WCOrderModel::class.java, WCOrderNoteModel::class.java, WCOrderStatusModel::class.java),
                WellSqlConfig.ADDON_WOOCOMMERCE)
        WellSql.init(config)
        config.reset()
    }

    @Test
    fun testInsertOrUpdateOrder() {
        val orderModel = OrderTestUtils.generateSampleOrder(42)
        val site = SiteModel().apply { id = orderModel.localSiteId }

        // Test inserting order
        OrderSqlUtils.insertOrUpdateOrder(orderModel)
        val storedOrders = OrderSqlUtils.getOrdersForSite(site)
        assertEquals(1, storedOrders.size)
        assertEquals(42, storedOrders[0].remoteOrderId)
        assertEquals(orderModel, storedOrders[0])

        // Test updating order
        storedOrders[0].apply {
            status = "processing"
            customerNote = "please gift wrap"
        }.also {
            OrderSqlUtils.insertOrUpdateOrder(it)
        }
        val updatedOrders = OrderSqlUtils.getOrdersForSite(site)
        assertEquals(1, storedOrders.size)
        assertEquals(storedOrders[0].id, updatedOrders[0].id)
        assertEquals(storedOrders[0].customerNote, updatedOrders[0].customerNote)
        assertEquals(storedOrders[0].status, updatedOrders[0].status)
    }

    @Test
    fun testGetOrdersForSite() {
        val processingOrder = OrderTestUtils.generateSampleOrder(3)
        val onHoldOrder = OrderTestUtils.generateSampleOrder(4, CoreOrderStatus.ON_HOLD.value)
        val cancelledOrder = OrderTestUtils.generateSampleOrder(5, CoreOrderStatus.CANCELLED.value)
        OrderSqlUtils.insertOrUpdateOrder(processingOrder)
        OrderSqlUtils.insertOrUpdateOrder(onHoldOrder)
        OrderSqlUtils.insertOrUpdateOrder(cancelledOrder)
        val site = SiteModel().apply { id = processingOrder.localSiteId }

        // Test getting orders without specifying a status
        val storedOrders = OrderSqlUtils.getOrdersForSite(site)
        assertEquals(3, storedOrders.size)

        // Test pulling orders with a single status specified
        val processingOrders = OrderSqlUtils.getOrdersForSite(site, listOf(CoreOrderStatus.PROCESSING.value))
        assertEquals(1, processingOrders.size)

        // Test pulling orders with multiple statuses specified
        val mixStatusOrders = OrderSqlUtils
                .getOrdersForSite(site, listOf(CoreOrderStatus.ON_HOLD.value, CoreOrderStatus.CANCELLED.value))
        assertEquals(2, mixStatusOrders.size)
    }

    @Test
    fun testDeleteOrdersForSite() {
        val order1 = OrderTestUtils.generateSampleOrder(1)
        val order2 = OrderTestUtils.generateSampleOrder(2)
        OrderSqlUtils.insertOrUpdateOrder(order1)
        OrderSqlUtils.insertOrUpdateOrder(order2)
        val site = SiteModel().apply { id = order1.localSiteId }

        val storedOrders = OrderSqlUtils.getOrdersForSite(site)
        assertEquals(2, storedOrders.size)

        val deletedCount = OrderSqlUtils.deleteOrdersForSite(site)
        assertEquals(2, deletedCount)

        val deletedOrders = OrderSqlUtils.getOrdersForSite(site)
        assertEquals(0, deletedOrders.size)
    }

    @Test
    fun testInsertOrIgnoreOrderNotes() {
        val order = OrderTestUtils.generateSampleOrder(42)
        val note1 = OrderTestUtils.generateSampleNote(1, order.localSiteId, order.id)
        val note2 = OrderTestUtils.generateSampleNote(2, order.localSiteId, order.id)

        // Test inserting notes
        OrderSqlUtils.insertOrIgnoreOrderNotes(listOf(note1, note2))
        val storedNotes = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes.size)

        // Test ignoring notes already saved to db
        val inserted = OrderSqlUtils.insertOrIgnoreOrderNotes(listOf(note1))
        assertEquals(0, inserted)
        val storedNotes2 = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes2.size)
    }

    @Test
    fun testInsertOrIgnoreOrderNote() {
        val order = OrderTestUtils.generateSampleOrder(42)
        val note1 = OrderTestUtils.generateSampleNote(1, order.localSiteId, order.id)
        val note2 = OrderTestUtils.generateSampleNote(2, order.localSiteId, order.id)

        // Test inserting notes
        OrderSqlUtils.insertOrIgnoreOrderNote(note1)
        OrderSqlUtils.insertOrIgnoreOrderNote(note2)
        val storedNotes = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes.size)

        // Test ignoring notes already saved to db
        val inserted = OrderSqlUtils.insertOrIgnoreOrderNote(note1)
        assertEquals(0, inserted)
        val storedNotes2 = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes2.size)
    }

    @Test
    fun testGetOrderNotesForOrder() {
        val order = OrderTestUtils.generateSampleOrder(42)
        val note1 = OrderTestUtils.generateSampleNote(1, order.localSiteId, order.id)
        val note2 = OrderTestUtils.generateSampleNote(2, order.localSiteId, order.id)
        OrderSqlUtils.insertOrIgnoreOrderNotes(listOf(note1, note2))

        val storedNotes = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes.size)
    }

    @Test
    fun testDeleteOrderNotesForSite() {
        val order = OrderTestUtils.generateSampleOrder(42)
        val note1 = OrderTestUtils.generateSampleNote(1, order.localSiteId, order.id)
        val note2 = OrderTestUtils.generateSampleNote(2, order.localSiteId, order.id)
        OrderSqlUtils.insertOrIgnoreOrderNotes(listOf(note1, note2))
        val site = SiteModel().apply { id = order.localSiteId }

        val storedNotes = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(2, storedNotes.size)

        val deletedCount = OrderSqlUtils.deleteOrderNotesForSite(site)
        assertEquals(2, deletedCount)
        val verify = OrderSqlUtils.getOrderNotesForOrder(order.id)
        assertEquals(0, verify.size)
    }

    @Test
    fun testInsertOrUpdateOrderStatusOptions() {
        val siteModel = SiteModel().apply { id = 1 }
        val optionsJson = UnitTestUtils.getStringFromResourceFile(this.javaClass, "wc/order_status_options.json")
        val orderStatusOptions = OrderTestUtils.getOrderStatusOptionsFromJson(optionsJson, siteModel.id)
        assertEquals(8, orderStatusOptions.size)

        // Save first option to the database, retrieve and verify fields
        val firstOption = orderStatusOptions[0]
        var rowsAffected = OrderSqlUtils.insertOrUpdateOrderStatusOption(firstOption)
        assertEquals(1, rowsAffected)
        val firstOptionDb = OrderSqlUtils.getOrderStatusOptionsForSite(siteModel).first()
        assertNotNull(firstOptionDb)
        assertEquals(firstOptionDb.localSiteId, firstOption.id)
        assertEquals(firstOptionDb.statusKey, firstOption.statusKey)
        assertEquals(firstOptionDb.label, firstOption.label)

        // Save full list, but only all but the first 2 should be inserted
        rowsAffected = orderStatusOptions.sumBy { OrderSqlUtils.insertOrUpdateOrderStatusOption(it) }
        assertEquals(8, rowsAffected)

        // Update a single option
        val newLabel = "New Label"
        firstOption.apply { label = newLabel }
        rowsAffected = OrderSqlUtils.insertOrUpdateOrderStatusOption(firstOption)
        assertEquals(1, rowsAffected)
        val newFirstOption = OrderSqlUtils.getOrderStatusOptionsForSite(siteModel).first {
            it.statusKey == firstOption.statusKey
        }
        assertEquals(firstOption.label, newFirstOption.label)

        // Get order status options from the database
        val orderStatusOptionsDb = OrderSqlUtils.getOrderStatusOptionsForSite(siteModel)
        assertEquals(8, orderStatusOptionsDb.size)
    }

    @Test
    fun testDeleteOrderStatusOption() {
        val siteModel = SiteModel().apply { id = 1 }
        val optionsJson = UnitTestUtils.getStringFromResourceFile(this.javaClass, "wc/order_status_options.json")
        val orderStatusOptions = OrderTestUtils.getOrderStatusOptionsFromJson(optionsJson, siteModel.id)
        assertEquals(8, orderStatusOptions.size)

        // Save the full list to the database
        var rowsAffected = orderStatusOptions.sumBy { OrderSqlUtils.insertOrUpdateOrderStatusOption(it) }
        assertEquals(8, rowsAffected)

        // Delete the first option from the database
        val firstOption = orderStatusOptions[0]
        rowsAffected = OrderSqlUtils.deleteOrderStatusOption(firstOption)
        assertEquals(1, rowsAffected)

        // Fetch list and verify first option is not present
        val allOptions = OrderSqlUtils.getOrderStatusOptionsForSite(siteModel)
        assertEquals(7, allOptions.size)
        assertFalse { allOptions.contains(firstOption) }
    }

    @Test
    fun testGetOrderStatusOption() {
        val siteModel = SiteModel().apply { id = 1 }
        val optionsJson = UnitTestUtils.getStringFromResourceFile(this.javaClass, "wc/order_status_options.json")
        val orderStatusOptions = OrderTestUtils.getOrderStatusOptionsFromJson(optionsJson, siteModel.id)
        assertEquals(8, orderStatusOptions.size)

        // Save the full list to the database
        val rowsAffected = orderStatusOptions.sumBy { OrderSqlUtils.insertOrUpdateOrderStatusOption(it) }
        assertEquals(8, rowsAffected)

        // Get the first option from the database by the status key
        val firstOption = OrderSqlUtils.getOrderStatusOptionForSiteByKey(siteModel, orderStatusOptions[0].statusKey)
        assertNotNull(firstOption)
        assertEquals(firstOption.label, orderStatusOptions[0].label)
    }

    @Test
    fun testGetOrderStatusOptions_Empty() {
        val siteModel = SiteModel().apply { id = 1 }

        // Attempt to fetch order status options from database.
        // No options will be available.
        val options = OrderSqlUtils.getOrderStatusOptionsForSite(siteModel)
        assertNotNull(options)
        assertEquals(0, options.size)
    }

    @Test
    fun testGetOrderStatusOption_NotExists() {
        val siteModel = SiteModel().apply { id = 1 }

        // Get the first option from the database by the status key
        val option = OrderSqlUtils.getOrderStatusOptionForSiteByKey(siteModel, "missing")
        assertNull(option)
    }

    @Test
    fun testGetOrderShipmentTrackingsForOrder() {
        val siteModel = SiteModel().apply { id = 1 }
        val orderModel = OrderTestUtils.generateSampleOrder(3, siteId = 1)
        val json = UnitTestUtils
                .getStringFromResourceFile(this.javaClass, "wc/order-shipment-trackings-multiple.json")
        val trackings = OrderTestUtils.
                getOrderShipmentTrackingsFromJson(json, siteModel.id, orderModel.id)
                .toMutableList()
        assertEquals(2, trackings.size)

        // Save full list to the database
        var rowsAffected = trackings.sumBy { OrderSqlUtils.insertOrIgnoreOrderShipmentTracking(it) }
        assertEquals(2, rowsAffected)

        // Attempt to save again (should ignore both existing entries and add new one)
        trackings[2] = OrderTestUtils.generateOrderShipmentTracking(siteModel.id, orderModel.id)
        rowsAffected = trackings.sumBy { OrderSqlUtils.insertOrIgnoreOrderShipmentTracking(it) }
        assertEquals(1, rowsAffected)

        // Get all shipment trackings for a single order
        val trackingsForOrder = OrderSqlUtils.getShipmentTrackingsForOrder(orderModel)
        assertEquals(3, trackingsForOrder.size)
    }
}

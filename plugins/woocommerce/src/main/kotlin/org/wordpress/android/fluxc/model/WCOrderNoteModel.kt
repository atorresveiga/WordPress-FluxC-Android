package org.wordpress.android.fluxc.model

import com.yarolegovich.wellsql.core.Identifiable
import com.yarolegovich.wellsql.core.annotation.Column
import com.yarolegovich.wellsql.core.annotation.PrimaryKey
import com.yarolegovich.wellsql.core.annotation.Table
import org.wordpress.android.fluxc.persistence.WellSqlConfig

@Table(addOn = WellSqlConfig.ADDON_WOOCOMMERCE)
data class WCOrderNoteModel(@PrimaryKey @Column private var id: Int = 0) : Identifiable {
    @Column var localSiteId = 0
    @Column var localOrderId = 0 // The local db unique identifier for the parent order object
    @Column var remoteNoteId = 0L // The unique identifier for this note on the server
    @Column var dateCreated = "" // ISO 8601-formatted date in UTC, e.g. 1955-11-05T14:15:00Z
    @Column var note = ""
    @Column var isCustomerNote = false // False if private, else customer-facing. Default is false

    override fun getId() = id

    override fun setId(id: Int) {
        this.id = id
    }

    /**
     * Kotlin auto-generates boolean setters by dropping the "is" prefix. So the property `isCustomerNote` turns
     * into `setCustomerNote(...)`. WellSql expects as `setIsCustomerNote(...)` method to exist and calls
     * it from the auto-generated mapper. This little workaround ensures the auto-generated mapper still
     * works.
     */
    fun setIsCustomerNote(value: Boolean) {
        isCustomerNote = value
    }
}

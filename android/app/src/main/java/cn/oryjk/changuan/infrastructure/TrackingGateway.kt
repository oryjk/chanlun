package cn.oryjk.changuan.infrastructure

import cn.oryjk.changuan.application.TrackingCommands
import cn.oryjk.changuan.application.TrackingReader
import cn.oryjk.changuan.domain.TrackedAsset
import cn.oryjk.changuan.domain.TrackingOverview
import com.google.gson.JsonArray
import com.google.gson.JsonObject

class TrackingGateway(
    private val api: ApiClient,
) : TrackingReader, TrackingCommands {
    override suspend fun fetchTrackings(): TrackingOverview =
        api.send(
            path = "api/app-tracking",
            authenticated = true,
            mapper = ::parseOverview,
        )

    override suspend fun startTracking(secid: String) {
        val body = JsonObject().apply {
            addProperty("secid", secid)
        }
        api.send(
            path = "api/app-tracking/start",
            body = body,
            authenticated = true,
        ) { }
    }

    override suspend fun cancelTrackings(secids: List<String>) {
        if (secids.isEmpty()) return
        val array = JsonArray().apply {
            secids.forEach(::add)
        }
        val body = JsonObject().apply {
            add("secids", array)
        }
        api.send(
            path = "api/app-tracking/cancel",
            body = body,
            authenticated = true,
        ) { }
    }

    companion object {
        internal fun parseOverview(obj: JsonObject): TrackingOverview =
            TrackingOverview(
                asOfDate = obj.optString("as_of_date").orEmpty(),
                items = obj.optArray("items").map { row ->
                    TrackedAsset(
                        stock = MarketGateway.parseStock(row.reqObject("stock")),
                        startDate = row.reqString("start_date"),
                        startPrice = row.reqDouble("start_price"),
                        latestPrice = row.reqDouble("latest_price"),
                        dayReturnPct = row.optDouble("day_return_pct") ?: 0.0,
                        dayProfit = row.optDouble("day_profit") ?: 0.0,
                        cumulativeReturnPct = row.optDouble("cumulative_return_pct") ?: 0.0,
                        cumulativeProfit = row.optDouble("cumulative_profit") ?: 0.0,
                        currentValue = row.reqDouble("current_value"),
                        sparkline = row.optDoubleList("sparkline"),
                    )
                },
            )
    }
}

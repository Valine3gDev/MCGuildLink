package io.github.valine3gdev.mcguildlink.app.service


fun interface WhitelistRefreshRequester {
    fun requestRefresh()
}


internal object NoopWhitelistRefreshRequester : WhitelistRefreshRequester {
    override fun requestRefresh() = Unit
}

package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
import androidx.lifecycle.viewModelScope
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationSnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.local.HouseholdListingCacheDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.AppDatabase
import com.irinteractivestudios.kabadiwalaconnect.data.local.toCacheEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.toHouseholdListing
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.PendingPhotoUploadDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.PendingPhotoUploadEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.RoomSupplySnapshotStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.SupplySnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.auth.SessionSnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.util.ImagePipeline
import com.irinteractivestudios.kabadiwalaconnect.util.UiActionTrace
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.UUID

data class SupplyChainState(
    val loading: Boolean = true,
    val householdListingsLoading: Boolean = true,
    val householdListingsLoaded: Boolean = false,
    val initialLoadComplete: Boolean = false,
    val error: String? = null,
    val listings: List<HouseholdListingDto> = emptyList(),
    val householdListingsNextCursor: String? = null,
    val householdPickupsNextCursor: String? = null,
    val collectorAssignedNextCursor: String? = null,
    val collectorWaitingNextCursor: String? = null,
    val collectorHistoryNextCursor: String? = null,
    val kabadiwalas: List<KabadiwalaProfileDto> = emptyList(),
    val kabadiwalaRadiusKm: Int = 25,
    val kabadiwalaAreaQuery: String = "",
    val kabadiwalaLatitude: Double? = null,
    val kabadiwalaLongitude: Double? = null,
    val kabadiwalaPage: Int = 1,
    val kabadiwalaNextCursor: String? = null,
    val kabadiwalaHasMore: Boolean = false,
    val kabadiwalaLoading: Boolean = false,
    val kabadiwalaRequiresLocation: Boolean = false,
    val selectedKabadiwalaId: String? = null,
    val kabadiwalaProfile: KabadiwalaPublicProfileDto? = null,
    val kabadiwalaProfileLoading: Boolean = false,
    val pickups: List<PickupRequestDto> = emptyList(),
    val householdPickupQr: HouseholdPickupQrDto? = null,
    val householdPickupQrLoadingId: String? = null,
    val householdPickupQrError: String? = null,
    val pendingPickupListingIds: Set<String> = emptySet(),
    val inventory: List<InventoryBalanceDto> = emptyList(),
    val inventoryMovements: List<InventoryMovementDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val collectorLotsNextCursor: String? = null,
    val collectorOffersNextCursor: String? = null,
    val requirements: List<ProcurementRequirementDto> = emptyList(),
    val recyclerLotsNextCursor: String? = null,
    val recyclerOffersNextCursor: String? = null,
    val recyclerRequirementsNextCursor: String? = null,
    val routeAdvantage: RouteAdvantageResponseDto? = null,
    val poolOpportunities: List<PoolOpportunityDto> = emptyList(),
    val poolSuggestions: List<PoolSuggestionDto> = emptyList(),
    val demandIntelligence: List<JsonObject> = emptyList(),
    val pools: List<PooledConsignmentDto> = emptyList(),
    val handovers: List<SupplyHandoverDto> = emptyList(),
    val passport: CollectorPassportDto? = null,
    val safety: SafetyResponseDto? = null,
    val safetyRouting: SafetyRoutingResponseDto? = null,
    val materialPassports: Map<String, MaterialPassportResponseDto> = emptyMap(),
    val anomalies: Map<String, AnomalyResponseDto> = emptyMap(),
    val showingCachedEvidence: Boolean = false,
    val cachedAtEpochMs: Long = 0L,
    val busy: Set<String> = emptySet(),
    val notice: String? = null,
    val pendingPhotoUpload: PendingPhotoUpload? = null,
    val materialSuggestion: MaterialSuggestionDto? = null,
    val materialDetectionPath: String? = null,
    val materialDetectionStatus: HouseholdMaterialDetectionStatus = HouseholdMaterialDetectionStatus.IDLE,
    val materialDetectionMessage: String? = null,
    val householdPriceEstimate: HouseholdPriceEstimate? = null,
    val householdPriceEstimateLoading: Boolean = false,
    val householdPriceEstimateMessage: String? = null,
    val listingPhotos: Map<String, List<ByteArray>> = emptyMap(),
    val listingPhotoErrors: Map<String, String> = emptyMap()
)

enum class HouseholdMaterialDetectionStatus { IDLE, PROCESSING, SUCCESS, LOW_CONFIDENCE, UNSUPPORTED_IMAGE, NETWORK_ERROR, SERVICE_ERROR }

data class PendingPhotoUpload(val listingId: String, val localPaths: List<String>)

data class HouseholdPriceEstimate(
    val materialCategory: String,
    val weightKg: Double,
    val condition: String,
    val areaName: String,
    val minimum: Double,
    val maximum: Double,
    val disclaimer: String,
    val source: String = "VERIFIED_LOCAL"
)

class SupplyChainViewModel(
    private val api: ApiService,
    private val roleProvider: () -> AccountRole? = { null },
    private val cache: FormalisationCacheStore? = null,
    private val accountIdProvider: () -> String? = { null },
    private val idempotencyKeys: IdempotencyKeyStore? = null,
    private val syncQueue: SyncQueueDao? = null,
    private val requestSync: (() -> Unit)? = null,
    private val pendingPhotoUploads: PendingPhotoUploadDao? = null,
    private val householdListingCache: HouseholdListingCacheDao? = null,
    /**
     * A restored navigation stack can briefly compose a protected screen
     * before MainActivity has finished validating the persisted session. Keep
     * the final network gate in the ViewModel as well as in navigation so a
     * stale collector screen cannot issue an unauthenticated request.
     */
    private val authenticatedSessionReady: () -> Boolean = { true },
    private val languageProvider: () -> String = { LocaleManager.ENGLISH },
    private val initialHouseholdArea: () -> String? = { null },
    private val initialHouseholdLocation: () -> CurrentLocation? = { null },
    private val sessionSnapshots: StateFlow<SessionSnapshot>? = null,
    private val supplySnapshotStore: RoomSupplySnapshotStore? = null,
    private val localDatabase: AppDatabase? = null
) : ViewModel() {
    private fun newHouseholdSearchState() = initialHouseholdLocation().let { location ->
        SupplyChainState(
            kabadiwalaAreaQuery = initialHouseholdArea().orEmpty(),
            kabadiwalaLatitude = location?.latitude,
            kabadiwalaLongitude = location?.longitude
        )
    }
    private val _state = MutableStateFlow(newHouseholdSearchState())
    val state: StateFlow<SupplyChainState> = _state.asStateFlow()
    private var stateAccountId: String? = null
    private var householdRefreshGeneration = 0L
    private var directorySearchGeneration = 0L
    private var directorySearchJob: Job? = null
    private var directoryPageJob: Job? = null
    private var profileLoadGeneration = 0L
    private var profileLoadJob: Job? = null
    private var householdRefreshJob: Job? = null
    private var pendingHouseholdRefresh: HouseholdRefreshRequest? = null
    private var householdQueueObservationJob: Job? = null
    private var householdQueueObservationAccountId: String? = null
    private var observedAuthenticatedAccountId: String? = null
    private var supplyRefreshGeneration = 0L
    private var supplyRefreshJob: Job? = null
    private var lastRefreshStartedAt = 0L
    private var materialDetectionJob: Job? = null
    private var householdPriceEstimateJob: Job? = null
    private val persistenceMutex = Mutex()
    private val actionJobs = mutableMapOf<String, Job>()
    private val terminalCollectorPickupStatuses = setOf("COMPLETED", "CANCELLED", "REJECTED")

    private data class HouseholdRefreshRequest(val radiusKm: Int, val areaQuery: String)

    init {
        observePendingPickupQueue(accountId())
        viewModelScope.launch {
            ConfirmedHandoverEvents.events.collect { event ->
                if (event.accountId == accountId() && protectedSessionReady()) {
                    supplyRefreshGeneration++
                    supplyRefreshJob?.cancel()
                    supplyRefreshJob = null
                    val handover = event.handover.copy(payments = event.handover.payments.ifEmpty { _state.value.handovers.firstOrNull { it.id == event.handover.id }?.payments.orEmpty() })
                    _state.value = _state.value.copy(
                        handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id },
                        bulkLots = _state.value.bulkLots.map {
                            if (it.id == handover.bulkLotId && handover.status == "COMPLETED") it.copy(status = "SOLD") else it
                        },
                        offers = _state.value.offers.map { if (it.bulkLotId == handover.bulkLotId && handover.status == "COMPLETED" && it.status == "ACCEPTED") it.copy(status = "COMPLETED") else it })
                }
            }
        }
        // Screen effects can run while a reconnect is closing the session
        // gate, then never run again on the same nav entry. A new authenticated
        // snapshot always starts the role's first request from the ViewModel.
        sessionSnapshots?.let { snapshots ->
            viewModelScope.launch {
                snapshots.collect { snapshot ->
                    val account = snapshot.account
                    if (!snapshot.restorable || account == null) {
                        observedAuthenticatedAccountId = null
                        actionJobs.values.toList().forEach(Job::cancel)
                        actionJobs.clear()
                        observePendingPickupQueue(null)
                        householdRefreshGeneration++
                        pendingHouseholdRefresh = null
                        householdRefreshJob?.cancel()
                        householdRefreshJob = null
                        directorySearchGeneration++
                        directorySearchJob?.cancel()
                        directoryPageJob?.cancel()
                        profileLoadGeneration++
                        profileLoadJob?.cancel()
                        supplyRefreshGeneration++
                        supplyRefreshJob?.cancel()
                    } else if (observedAuthenticatedAccountId != account.profileId && accountId() == account.profileId) {
                        // Profile saves and verification updates publish a new
                        // account snapshot. They do not invalidate all supply
                        // data or justify another full dashboard request fanout.
                        observedAuthenticatedAccountId = account.profileId
                        observePendingPickupQueue(account.profileId.takeIf { account.role == AccountRole.HOUSEHOLD })
                        when (account.role) {
                            AccountRole.HOUSEHOLD -> refreshHousehold()
                            AccountRole.COLLECTOR -> refreshKabadiwala()
                            AccountRole.RECYCLER -> refreshRecycler()
                            AccountRole.ADMIN -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun friendly(error: Throwable): String {
        val remote = error as? RemoteApiException
        val message = remote?.message.orEmpty()
        val code = remote?.code.orEmpty()
        return when {
            message.contains("no longer available", ignoreCase = true) || message.contains("not available to accept", ignoreCase = true) -> "Another Kabadiwala already took this pickup. The queue has been refreshed."
            remote?.detailsCode == "PICKUP_OUTSIDE_SERVICE_AREA" || message.contains("outside your configured service distance", ignoreCase = true) || message.contains("outside your service area", ignoreCase = true) -> "This pickup is outside your configured service distance. Refresh the pickup list to see requests in your area."
            message.contains("household QR", ignoreCase = true) && message.contains("before", ignoreCase = true) -> "Ask the household to show its pickup QR, then scan it before weighing."
            message.contains("QR is invalid or expired", ignoreCase = true) -> "This QR is invalid or expired. Ask the household to refresh it and scan again."
            message.contains("reason code is required", ignoreCase = true) -> "Choose why the final material, weight, or value differs from the listing."
            message.contains("already been scanned", ignoreCase = true) -> "This pickup was already verified. Refresh the pickup list to see the update."
            code in setOf("HTTP_401", "AUTHENTICATION_REQUIRED", "TOKEN_EXPIRED") || remote?.httpCode == 401 -> error.toAppFailure().message
            remote?.code == "HTTP_403" || remote?.httpCode == 403 -> error.toAppFailure().message
            code == "EMPTY_RESPONSE" -> "The server returned an incomplete response. Please try again."
            code in setOf("INVALID_PHOTO", "PHOTO_REQUIRED", "PHOTO_UPLOAD_FAILED") -> "That photo could not be uploaded. Choose another clear image and retry."
            code == "PHOTO_STORAGE_UNAVAILABLE" -> "Photo upload is temporarily unavailable. Your listing was not posted; try again later."
            code == "IDEMPOTENCY_KEY_REUSED" -> "This listing changed while it was being posted. Retry once; if it continues, start a new listing."
            remote?.httpCode == 409 -> message.ifBlank { "That record changed. Refresh and try again." }
            remote?.httpCode == 422 -> message.takeUnless { it == "Invalid request" }.orEmpty().ifBlank { "Review the required details and try again." }
            error is IllegalStateException && error.message?.contains("photo", ignoreCase = true) == true -> "The selected photo is no longer available. Choose it again."
            error is IOException -> error.toAppFailure().message
            remote?.httpCode != null && remote.httpCode >= 500 -> "The server couldn't finish this action. Check your connection and try again."
            !remote?.message.isNullOrBlank() -> remote.message
            else -> "Could not complete this action. Please try again."
        }
    }

    private fun householdRefreshError(error: Throwable, section: String): String {
        val remoteCode = (error as? RemoteApiException)?.code
        return when {
            remoteCode in setOf("HTTP_401", "AUTHENTICATION_REQUIRED", "TOKEN_EXPIRED", "HTTP_403", "HTTP_409", "HTTP_422") -> friendly(error)
            error is IOException -> friendly(error)
            remoteCode == "EMPTY_RESPONSE" -> "The server returned an incomplete response for $section. Please try again."
            else -> "Could not load $section. Please try again."
        }
    }

    private fun allowed(role: AccountRole): Boolean = roleProvider()?.let { it == role } ?: true

    private fun protectedSessionReady(): Boolean = authenticatedSessionReady()

    private fun accountId() = accountIdProvider()

    private fun observePendingPickupQueue(account: String?) {
        val normalized = account?.takeIf { it.isNotBlank() }
        if (normalized == householdQueueObservationAccountId) return
        householdQueueObservationAccountId = normalized
        householdQueueObservationJob?.cancel()
        householdQueueObservationJob = null
        if (normalized == null || syncQueue == null) {
            _state.value = _state.value.copy(pendingPickupListingIds = emptySet())
            return
        }
        householdQueueObservationJob = viewModelScope.launch {
            syncQueue.observeForAccount(normalized).collect { items ->
                val pending = items.asSequence()
                    .filter { it.operation == "REQUEST_HOUSEHOLD_PICKUP" && (it.lastErrorCode == null || it.attempts < 3) }
                    .mapNotNull { item ->
                        runCatching {
                            JsonParser.parseString(item.payloadJson).asJsonObject.get("listingId")?.asString
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                    }
                    .toSet()
                if (accountId() == normalized) {
                    _state.value = _state.value.copy(pendingPickupListingIds = pending)
                }
            }
        }
    }

    /** A ViewModel can outlive a logout/account switch while its nav entry is
     * still retained. Never let account-scoped photos, errors, or lists bleed
     * into the next authenticated identity. */
    private fun resetForAccountChange() {
        val current = accountId()?.takeIf { it.isNotBlank() }
        if (current == stateAccountId) return
        actionJobs.values.toList().forEach(Job::cancel)
        actionJobs.clear()
        householdRefreshGeneration++
        pendingHouseholdRefresh = null
        householdRefreshJob?.cancel()
        householdRefreshJob = null
        directorySearchGeneration++
        directorySearchJob?.cancel()
        directoryPageJob?.cancel()
        profileLoadGeneration++
        profileLoadJob?.cancel()
        supplyRefreshGeneration++
        supplyRefreshJob?.cancel()
        materialDetectionJob?.cancel()
        householdPriceEstimateJob?.cancel()
        materialDetectionJob = null
        householdPriceEstimateJob = null
        stateAccountId = current
        _state.value = newHouseholdSearchState()
        observePendingPickupQueue(current.takeIf { roleProvider() == AccountRole.HOUSEHOLD })
    }

    private suspend fun cachedHouseholdListings(): List<HouseholdListingDto> {
        val account = accountId()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val rows = householdListingCache?.findForAccount(account).orEmpty()
        return rows.map { it.toHouseholdListing() }
    }

    /**
     * Merge a successful server read without dropping drafts that are still
     * waiting in the account-scoped outbox. A process restart can therefore
     * render the user's offline listing before the network comes back.
     */
    private suspend fun mergeHouseholdListings(remote: List<HouseholdListingDto>): List<HouseholdListingDto> {
        val account = accountId()?.takeIf { it.isNotBlank() } ?: return remote
        val cache = householdListingCache ?: return remote
        val local = cache.findForAccount(account).map { it.toHouseholdListing() }
        val remoteIds = remote.mapTo(HashSet(remote.size)) { it.id }
        val pending = local.filter { cached -> cached.id !in remoteIds && cached.status == "PENDING_SYNC" }
        cache.upsertAll(remote.map { it.toCacheEntity(account, synced = true) })
        cache.pruneSyncedForAccount(account)
        return (remote + pending).distinctBy { it.id }
    }

    private suspend fun restorePendingPhotoUpload() {
        val account = accountId() ?: return
        val pending = pendingPhotoUploads?.findForAccount(account) ?: return
        val paths = pending.localPathsJson?.let { runCatching { Gson().fromJson(it, Array<String>::class.java).toList() }.getOrNull() }
            ?.ifEmpty { listOf(pending.localPath) }
            ?: listOf(pending.localPath)
        if (paths.all { File(it).isFile }) {
            _state.value = _state.value.copy(
                pendingPhotoUpload = PendingPhotoUpload(pending.listingId, paths)
            )
        } else {
            // The app-private file was removed externally; do not leave a
            // retry action that can never succeed.
            pendingPhotoUploads.remove(account, pending.listingId)
        }
    }

    private fun applyCached(snapshot: FormalisationSnapshot) {
        _state.value = _state.value.copy(
            routeAdvantage = snapshot.routeAdvantage,
            poolOpportunities = snapshot.poolOpportunities,
            pools = _state.value.pools.ifEmpty { snapshot.pools },
            bulkLots = if (_state.value.bulkLots.isEmpty()) snapshot.bulkLots else _state.value.bulkLots,
            offers = if (_state.value.offers.isEmpty()) snapshot.offers else _state.value.offers,
            handovers = _state.value.handovers.ifEmpty { snapshot.handovers },
            passport = _state.value.passport ?: snapshot.passport,
            safety = _state.value.safety ?: snapshot.safety,
            showingCachedEvidence = true,
            cachedAtEpochMs = snapshot.cachedAtEpochMs
        )
    }

    private suspend fun saveCache() {
        val owner = accountId()
        val snapshot = FormalisationSnapshot(
            routeAdvantage = _state.value.routeAdvantage,
            poolOpportunities = _state.value.poolOpportunities,
            pools = _state.value.pools,
            bulkLots = _state.value.bulkLots,
            offers = _state.value.offers,
            handovers = _state.value.handovers,
            passport = _state.value.passport,
            safety = _state.value.safety
        )
        withContext(Dispatchers.IO) { cache?.save(owner, snapshot) }
    }

    /** Returning between tabs reuses fresh shared content; explicit refresh and events bypass this. */
    fun refreshIfStale() {
        if (!protectedSessionReady()) return
        resetForAccountChange()
        val visible = _state.value
        val hasContent = visible.initialLoadComplete || visible.pickups.isNotEmpty() || visible.listings.isNotEmpty() ||
            visible.inventory.isNotEmpty() || visible.bulkLots.isNotEmpty() || visible.offers.isNotEmpty()
        if (hasContent && System.currentTimeMillis() - lastRefreshStartedAt < 10_000) return
        when (roleProvider()) {
            AccountRole.HOUSEHOLD -> refreshHousehold()
            AccountRole.COLLECTOR -> refreshKabadiwala()
            AccountRole.RECYCLER -> refreshRecycler()
            else -> Unit
        }
    }

    fun refreshHousehold(radiusKm: Int? = null, areaQuery: String? = null) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        resetForAccountChange()
        val requestedRadiusKm = radiusKm ?: _state.value.kabadiwalaRadiusKm
        val requestedArea = areaQuery ?: _state.value.kabadiwalaAreaQuery
        if (householdRefreshJob?.isActive == true) {
            // Keep the newest requested filters and guarantee one trailing
            // refresh after the in-flight listings/pickups/directory pass.
            pendingHouseholdRefresh = HouseholdRefreshRequest(requestedRadiusKm, requestedArea)
            return
        }
        val generation = ++householdRefreshGeneration
        val directoryGeneration = directorySearchGeneration
        val refreshAccount = accountId()
        lastRefreshStartedAt = System.currentTimeMillis()
        householdRefreshJob = viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    loading = true,
                    householdListingsLoading = _state.value.listings.isEmpty(),
                    householdListingsLoaded = _state.value.listings.isNotEmpty(),
                    error = null
                )
                val cached = runCatching { cachedHouseholdListings() }.getOrDefault(emptyList())
                if (generation == householdRefreshGeneration && accountId() == refreshAccount && _state.value.listings.isEmpty() && cached.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        listings = cached,
                        householdListingsLoading = false,
                        householdListingsLoaded = true
                    )
                }
                fun current() = generation == householdRefreshGeneration && accountId() == refreshAccount && protectedSessionReady()
                val failures = mutableListOf<String>()
                fetch { restorePendingPhotoUpload() }
                // These reads are independent. Starting them together means a
                // slow pickup or directory response cannot delay the listing
                // result that the Household is waiting to see.
                val listingsRequest = async {
                    fetch { api.getHouseholdListings(limit = 50).also { it.requireData() } }.also { result ->
                        if (!current()) return@also
                        result.onSuccess { response ->
                            _state.value = _state.value.copy(
                                listings = mergeHouseholdListings(response.requireData()),
                                householdListingsNextCursor = response.body()?.page?.nextCursor,
                                householdListingsLoading = false,
                                householdListingsLoaded = true
                            )
                        }.onFailure { error ->
                            Log.e("HouseholdRefresh", "Failed loading listings (${error::class.java.simpleName})")
                            val message = householdRefreshError(error, "your listings")
                            failures += message
                            _state.value = _state.value.copy(householdListingsLoading = false, householdListingsLoaded = true, error = message)
                        }
                    }
                }
                val pickupsRequest = async {
                    fetch { api.getHouseholdPickups(limit = 50).also { it.requireData() } }.also { result ->
                        if (!current()) return@also
                        result.onSuccess { response -> _state.value = _state.value.copy(pickups = response.requireData(), householdPickupsNextCursor = response.body()?.page?.nextCursor) }
                            .onFailure { error ->
                                Log.e("HouseholdRefresh", "Failed loading pickups (${error::class.java.simpleName})")
                                failures += householdRefreshError(error, "your pickup history")
                            }
                    }
                }
                val directoryRequest = async {
                    fetch {
                        api.getHouseholdKabadiwalas(
                            _state.value.kabadiwalaLatitude,
                            _state.value.kabadiwalaLongitude,
                            requestedRadiusKm,
                            requestedArea.ifBlank { null }
                        ).requireData().toKabadiwalaDirectoryDto()
                    }.also { result ->
                        if (!current()) return@also
                        result.onSuccess { directory ->
                            if (directoryGeneration == directorySearchGeneration) _state.value = _state.value.copy(kabadiwalas = directory.items, kabadiwalaRadiusKm = requestedRadiusKm, kabadiwalaAreaQuery = requestedArea, kabadiwalaPage = directory.pagination.page, kabadiwalaNextCursor = directory.pagination.nextCursor, kabadiwalaHasMore = directory.pagination.nextCursor != null || directory.pagination.page < directory.pagination.totalPages, kabadiwalaRequiresLocation = directory.requiresLocation)
                        }.onFailure { error ->
                            Log.e("HouseholdRefresh", "Failed loading directory (${error::class.java.simpleName})")
                            failures += householdRefreshError(error, "nearby Kabadiwalas")
                        }
                    }
                }
                awaitAll(listingsRequest, pickupsRequest, directoryRequest)
                val listingsResult = listingsRequest.await()
                if (!current()) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    initialLoadComplete = failures.isEmpty() || _state.value.initialLoadComplete,
                    listings = if (listingsResult.isFailure) runCatching { cachedHouseholdListings() }.getOrDefault(cached).ifEmpty { _state.value.listings } else _state.value.listings,
                    // Listings, pickups and directory fail independently. A
                    // household should see one actionable offline message,
                    // not one copy per failed endpoint (or minor wording
                    // variation between them).
                    error = summarizeHouseholdRefreshFailures(failures)
                )
            } finally {
                if (generation == householdRefreshGeneration && accountId() == refreshAccount) {
                    householdRefreshJob = null
                    val trailing = pendingHouseholdRefresh
                    pendingHouseholdRefresh = null
                    if (trailing != null && protectedSessionReady()) refreshHousehold(trailing.radiusKm, trailing.areaQuery)
                }
            }
        }
    }

    private fun summarizeHouseholdRefreshFailures(failures: List<String>): String? {
        if (failures.isEmpty()) return null
        failures.firstOrNull { it.contains("session expired", ignoreCase = true) }?.let { return it }
        if (failures.any { it.contains("connection issue", ignoreCase = true) || it.contains("internet", ignoreCase = true) }) {
            return "Connection issue. Check your internet and retry."
        }
        return failures.first()
    }

    fun loadMoreHouseholdHistory() {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        val listingsCursor = _state.value.householdListingsNextCursor
        val pickupsCursor = _state.value.householdPickupsNextCursor
        if (listingsCursor == null && pickupsCursor == null) return
        val owner = accountId()
        val busyKey = "household-history-page"
        if (busyKey in _state.value.busy) return
        _state.value = _state.value.copy(busy = _state.value.busy + busyKey)
        viewModelScope.launch {
            try {
                val listingsRequest = listingsCursor?.let { cursor -> async { fetch { api.getHouseholdListings(limit = 50, cursor = cursor).also { it.requireData() } } } }
                val pickupsRequest = pickupsCursor?.let { cursor -> async { fetch { api.getHouseholdPickups(limit = 50, cursor = cursor).also { it.requireData() } } } }
                listingsRequest?.await()?.onSuccess { response ->
                    if (accountId() == owner && _state.value.householdListingsNextCursor == listingsCursor) {
                        _state.value = _state.value.copy(
                            listings = mergeHouseholdListings((_state.value.listings + response.requireData()).distinctBy { it.id }),
                            householdListingsNextCursor = response.body()?.page?.nextCursor
                        )
                    }
                }?.onFailure { if (accountId() == owner) _state.value = _state.value.copy(error = friendly(it)) }
                pickupsRequest?.await()?.onSuccess { response ->
                    if (accountId() == owner && _state.value.householdPickupsNextCursor == pickupsCursor) {
                        _state.value = _state.value.copy(
                            pickups = (_state.value.pickups + response.requireData()).distinctBy { it.id },
                            householdPickupsNextCursor = response.body()?.page?.nextCursor
                        )
                    }
                }?.onFailure { if (accountId() == owner) _state.value = _state.value.copy(error = friendly(it)) }
            } finally {
                if (accountId() == owner) _state.value = _state.value.copy(busy = _state.value.busy - busyKey)
            }
        }
    }

    fun searchHouseholdKabadiwalas(area: String, radiusKm: Int = _state.value.kabadiwalaRadiusKm, location: CurrentLocation? = null) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        resetForAccountChange()
        directorySearchJob?.cancel()
        directoryPageJob?.cancel()
        val generation = ++directorySearchGeneration
        val owner = accountId()
        val query = area.trim()
        if (location == null && query.isBlank()) {
            _state.value = _state.value.copy(kabadiwalaAreaQuery = "", kabadiwalaLatitude = null, kabadiwalaLongitude = null, kabadiwalas = emptyList(), kabadiwalaNextCursor = null, kabadiwalaRequiresLocation = true, kabadiwalaHasMore = false)
            return
        }
        _state.value = _state.value.copy(kabadiwalaAreaQuery = query, kabadiwalaLatitude = location?.latitude, kabadiwalaLongitude = location?.longitude, kabadiwalaRadiusKm = radiusKm, kabadiwalaNextCursor = null, kabadiwalaLoading = true, error = null)
        directorySearchJob = viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwalas(location?.latitude, location?.longitude, radiusKm, query.ifBlank { null }, 1).requireData().toKabadiwalaDirectoryDto() }
                .onSuccess { directory ->
                    if (generation == directorySearchGeneration && accountId() == owner) _state.value = _state.value.copy(kabadiwalas = directory.items, kabadiwalaPage = 1, kabadiwalaNextCursor = directory.pagination.nextCursor, kabadiwalaHasMore = directory.pagination.nextCursor != null || directory.pagination.page < directory.pagination.totalPages, kabadiwalaRequiresLocation = directory.requiresLocation, kabadiwalaLoading = false, error = null)
                }
                .onFailure { error -> if (error !is CancellationException && generation == directorySearchGeneration && accountId() == owner) _state.value = _state.value.copy(kabadiwalaLoading = false, error = friendly(error)) }
        }
    }
    fun loadMoreHouseholdKabadiwalas() {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        val current = _state.value
        if (current.kabadiwalaLoading || !current.kabadiwalaHasMore) return
        val generation = directorySearchGeneration
        val owner = accountId()
        _state.value = current.copy(kabadiwalaLoading = true)
        directoryPageJob = viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwalas(current.kabadiwalaLatitude, current.kabadiwalaLongitude, current.kabadiwalaRadiusKm, current.kabadiwalaAreaQuery.ifBlank { null }, current.kabadiwalaPage + 1, cursor = current.kabadiwalaNextCursor).requireData().toKabadiwalaDirectoryDto() }
                .onSuccess { directory ->
                    if (generation == directorySearchGeneration && accountId() == owner && _state.value.kabadiwalaPage == current.kabadiwalaPage) _state.value = _state.value.copy(kabadiwalas = (_state.value.kabadiwalas + directory.items).distinctBy { it.id }, kabadiwalaPage = current.kabadiwalaPage + 1, kabadiwalaNextCursor = directory.pagination.nextCursor, kabadiwalaHasMore = if (current.kabadiwalaNextCursor != null) directory.pagination.nextCursor != null else directory.pagination.page < directory.pagination.totalPages, kabadiwalaLoading = false)
                }
                .onFailure { error -> if (error !is CancellationException && generation == directorySearchGeneration && accountId() == owner) _state.value = _state.value.copy(kabadiwalaLoading = false, error = friendly(error)) }
        }
    }
    fun openKabadiwalaProfile(kabadiwalaId: String, latitude: Double? = null, longitude: Double? = null) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        profileLoadJob?.cancel()
        val generation = ++profileLoadGeneration
        val owner = accountId()
        _state.value = _state.value.copy(selectedKabadiwalaId = kabadiwalaId, kabadiwalaProfile = null, kabadiwalaProfileLoading = true, error = null)
        profileLoadJob = viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwala(kabadiwalaId, latitude ?: _state.value.kabadiwalaLatitude, longitude ?: _state.value.kabadiwalaLongitude).requireData() }
                .onSuccess { profile -> if (generation == profileLoadGeneration && accountId() == owner && _state.value.selectedKabadiwalaId == kabadiwalaId) _state.value = _state.value.copy(kabadiwalaProfile = profile, kabadiwalaProfileLoading = false) }
                .onFailure { error -> if (error !is CancellationException && generation == profileLoadGeneration && accountId() == owner) _state.value = _state.value.copy(kabadiwalaProfileLoading = false, error = friendly(error)) }
        }
    }
    fun closeKabadiwalaProfile() { profileLoadJob?.cancel(); profileLoadGeneration++; _state.value = _state.value.copy(selectedKabadiwalaId = null, kabadiwalaProfile = null, kabadiwalaProfileLoading = false) }
    fun increaseHouseholdRadius() {
        val next = when (_state.value.kabadiwalaRadiusKm) { 5 -> 10; 10 -> 25; 25 -> 50; 50 -> 100; 100 -> 200; else -> 200 }
        if (next != _state.value.kabadiwalaRadiusKm) searchHouseholdKabadiwalas(_state.value.kabadiwalaAreaQuery, next, _state.value.kabadiwalaLatitude?.let { CurrentLocation(it, _state.value.kabadiwalaLongitude ?: return, _state.value.kabadiwalaAreaQuery) })
    }
    fun refreshKabadiwala() {
        if (!allowed(AccountRole.COLLECTOR) || !protectedSessionReady()) return
        resetForAccountChange()
        // Mutations invalidate the old generation in action(). Screen/push
        // hints should share an existing read instead of repeatedly cancelling it.
        if (supplyRefreshJob?.isActive == true) return
        load { current -> coroutineScope {
        val savedSupply = withContext(Dispatchers.IO) { supplySnapshotStore?.load(accountId(), "COLLECTOR") }
        if (current() && savedSupply != null && _state.value.pickups.isEmpty() && _state.value.inventory.isEmpty()) {
            _state.value = _state.value.copy(listings = savedSupply.first.listings, pickups = savedSupply.first.pickups, inventory = savedSupply.first.inventory, cachedAtEpochMs = savedSupply.second, showingCachedEvidence = true)
        }
        val cached = withContext(Dispatchers.IO) { cache?.load(accountId()) }
        if (current() && !_state.value.initialLoadComplete) cached?.let(::applyCached)
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (error: Exception) { if (error is CancellationException) throw error; partialFailure = true; fallback }
        val previous = _state.value
        // Start independent requests together. The former serial chain made
        // every dashboard visit wait for the sum of fourteen network latencies.
        val assignedRequest = async {
            optional(previous.pickups.filter { it.status !in terminalCollectorPickupStatuses && it.kabadiwalaId != null }) {
                val response = api.getKabadiwalaPickups(limit = 50, scope = "assigned")
                val rows = response.requireData()
                if (current()) _state.value = _state.value.copy(collectorAssignedNextCursor = response.body()?.page?.nextCursor)
                rows
            }.also { if (current()) publishPickupSection(it, "assigned") }
        }
        val waitingRequest = async { optional(previous.pickups.filter { it.status == "WAITING_FOR_PICKUP" && it.kabadiwalaId == null }) { val response = api.getKabadiwalaPickups(limit = 50, scope = "waiting"); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(collectorWaitingNextCursor = response.body()?.page?.nextCursor); rows } .also { if (current()) publishPickupSection(it, "waiting") } }
        val historyRequest = async { optional(previous.pickups.filter { it.status in terminalCollectorPickupStatuses }) { val response = api.getKabadiwalaPickups(limit = 50, scope = "history"); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(collectorHistoryNextCursor = response.body()?.page?.nextCursor); rows } .also { if (current()) publishPickupSection(it, "history") } }
        val inventoryRequest = async { optional(previous.inventory) { api.getKabadiwalaInventory().requireData() }.also { if (current()) _state.value = _state.value.copy(inventory = it) } }
        val movementsRequest = async { optional(previous.inventoryMovements) { api.getInventoryMovements(limit = 100).requireData() } .also { if (current()) _state.value = _state.value.copy(inventoryMovements = it) } }
        val requirementsRequest = async { optional(previous.requirements) { api.getProcurementRequirements().requireData() } .also { if (current()) _state.value = _state.value.copy(requirements = it) } }
        val offersRequest = async { optional(previous.offers) { val response = api.getKabadiwalaBulkOffers(limit = 50); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(collectorOffersNextCursor = response.body()?.page?.nextCursor); rows }.also { if (current()) _state.value = _state.value.copy(offers = it) } }
        val bulkLotsRequest = async { optional(previous.bulkLots) { val response = api.getKabadiwalaBulkLots(limit = 50); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(collectorLotsNextCursor = response.body()?.page?.nextCursor); rows }.also { if (current()) _state.value = _state.value.copy(bulkLots = it) } }
        val opportunitiesRequest = async { optional(previous.poolOpportunities) { api.getPoolOpportunities().requireData() } .also { if (current()) _state.value = _state.value.copy(poolOpportunities = it) } }
        val suggestionsRequest = async { optional(previous.poolSuggestions) { api.getPoolSuggestions().requireData() } .also { if (current()) _state.value = _state.value.copy(poolSuggestions = it) } }
        val intelligenceRequest = async { optional(previous.demandIntelligence) { api.getDemandIntelligence().requireData() } .also { if (current()) _state.value = _state.value.copy(demandIntelligence = it) } }
        val poolsRequest = async { optional(previous.pools) { api.getKabadiwalaPools().requireData() } .also { if (current()) _state.value = _state.value.copy(pools = it) } }
        val handoversRequest = async { optional(previous.handovers) { api.getKabadiwalaHandovers().requireData() } .also { if (current()) _state.value = _state.value.copy(handovers = it) } }
        val passportRequest = async { optional(previous.passport) { api.getCollectorPassport().requireData() } .also { if (current()) _state.value = _state.value.copy(passport = it) } }
        val safetyRequest = async { optional(previous.safety) { api.getSafety().requireData() } .also { if (current()) _state.value = _state.value.copy(safety = it) } }
        val pickups = (assignedRequest.await() + waitingRequest.await() + historyRequest.await()).distinctBy { it.id }
        val listings = optional(previous.listings) {
            pickups.chunked(50).map { page -> async { api.getKabadiwalaListings(page.joinToString(",") { it.id }).requireData() } }.awaitAll().flatten()
        }
        if (current()) _state.value = _state.value.copy(listings = listings)
        val inventory = inventoryRequest.await()
        val inventoryMovements = movementsRequest.await()
        val requirements = requirementsRequest.await()
        val offers = offersRequest.await()
        val bulkLots = bulkLotsRequest.await()
        val opportunities = opportunitiesRequest.await()
        val suggestions = suggestionsRequest.await()
        val demandIntelligence = intelligenceRequest.await()
        val pools = poolsRequest.await()
        val handovers = handoversRequest.await()
        val passport = passportRequest.await()
        val safety = safetyRequest.await()
        if (!current()) return@coroutineScope
        _state.value = _state.value.copy(loading = false, initialLoadComplete = !partialFailure || previous.initialLoadComplete, listings = listings, pickups = pickups, inventory = inventory, inventoryMovements = inventoryMovements, bulkLots = bulkLots, offers = offers, requirements = requirements, poolOpportunities = opportunities, poolSuggestions = suggestions, demandIntelligence = demandIntelligence, pools = pools, handovers = handovers, passport = passport, safety = safety, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Could not load all current data. Check your connection and retry." else null)
        if (!partialFailure) {
            saveCache()
            withContext(Dispatchers.IO) { supplySnapshotStore?.save(accountId(), "COLLECTOR", SupplySnapshot(listings = listings, pickups = pickups, inventory = inventory, bulkLots = bulkLots, offers = offers)) }
        }
        } }
    }
    fun loadMoreCollectorLots() {
        val cursor = _state.value.collectorLotsNextCursor ?: return
        val generation = supplyRefreshGeneration
        action("more-collector-lots", AccountRole.COLLECTOR) {
            val owner = accountId()
            val response = api.getKabadiwalaBulkLots(limit = 50, cursor = cursor)
            val rows = response.requireData()
            if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.collectorLotsNextCursor == cursor) {
                _state.value = _state.value.copy(bulkLots = (_state.value.bulkLots + rows).distinctBy { it.id }, collectorLotsNextCursor = response.body()?.page?.nextCursor)
            }
            "Older lots loaded."
        }
    }
    fun loadMoreCollectorOffers() {
        val cursor = _state.value.collectorOffersNextCursor ?: return
        val generation = supplyRefreshGeneration
        action("more-collector-offers", AccountRole.COLLECTOR) {
            val owner = accountId()
            val response = api.getKabadiwalaBulkOffers(limit = 50, cursor = cursor)
            val rows = response.requireData()
            if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.collectorOffersNextCursor == cursor) {
                _state.value = _state.value.copy(offers = (_state.value.offers + rows).distinctBy { it.id }, collectorOffersNextCursor = response.body()?.page?.nextCursor)
            }
            "Older offers loaded."
        }
    }
    fun loadMoreCollectorPickups() {
        if (!allowed(AccountRole.COLLECTOR) || !protectedSessionReady()) return
        val assignedCursor = _state.value.collectorAssignedNextCursor
        val waitingCursor = _state.value.collectorWaitingNextCursor
        val historyCursor = _state.value.collectorHistoryNextCursor
        if (assignedCursor == null && waitingCursor == null && historyCursor == null) return
        val owner = accountId()
        val generation = supplyRefreshGeneration
        val busyKey = "collector-pickups-page"
        if (busyKey in _state.value.busy) return
        _state.value = _state.value.copy(busy = _state.value.busy + busyKey)
        viewModelScope.launch {
            try {
                val newPickupIds = mutableListOf<String>()
                val assignedRequest = assignedCursor?.let { cursor -> async { fetch { api.getKabadiwalaPickups(limit = 50, cursor = cursor, scope = "assigned").also { it.requireData() } } } }
                val waitingRequest = waitingCursor?.let { cursor -> async { fetch { api.getKabadiwalaPickups(limit = 50, cursor = cursor, scope = "waiting").also { it.requireData() } } } }
                val historyRequest = historyCursor?.let { cursor -> async { fetch { api.getKabadiwalaPickups(limit = 50, cursor = cursor, scope = "history").also { it.requireData() } } } }
                assignedRequest?.await()?.onSuccess { response ->
                    if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.collectorAssignedNextCursor == assignedCursor) {
                        newPickupIds += response.requireData().map { it.id }
                        val current = _state.value.pickups
                        _state.value = _state.value.copy(
                            pickups = (current.filter { it.kabadiwalaId != null && it.status !in terminalCollectorPickupStatuses } + response.requireData() + current.filter { it.kabadiwalaId == null || it.status in terminalCollectorPickupStatuses }).distinctBy { it.id },
                            collectorAssignedNextCursor = response.body()?.page?.nextCursor
                        )
                    }
                }?.onFailure { error -> if (error !is CancellationException && accountId() == owner && generation == supplyRefreshGeneration) _state.value = _state.value.copy(error = friendly(error)) }
                waitingRequest?.await()?.onSuccess { response ->
                    if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.collectorWaitingNextCursor == waitingCursor) {
                        newPickupIds += response.requireData().map { it.id }
                        val current = _state.value.pickups
                        _state.value = _state.value.copy(
                            pickups = (current.filter { it.kabadiwalaId != null && it.status !in terminalCollectorPickupStatuses } + current.filter { it.kabadiwalaId == null } + response.requireData() + current.filter { it.status in terminalCollectorPickupStatuses }).distinctBy { it.id },
                            collectorWaitingNextCursor = response.body()?.page?.nextCursor
                        )
                    }
                }?.onFailure { error -> if (error !is CancellationException && accountId() == owner && generation == supplyRefreshGeneration) _state.value = _state.value.copy(error = friendly(error)) }
                historyRequest?.await()?.onSuccess { response ->
                    if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.collectorHistoryNextCursor == historyCursor) {
                        newPickupIds += response.requireData().map { it.id }
                        _state.value = _state.value.copy(
                            pickups = (_state.value.pickups + response.requireData()).distinctBy { it.id },
                            collectorHistoryNextCursor = response.body()?.page?.nextCursor
                        )
                    }
                }?.onFailure { error -> if (error !is CancellationException && accountId() == owner && generation == supplyRefreshGeneration) _state.value = _state.value.copy(error = friendly(error)) }
                newPickupIds.distinct().chunked(50).forEach { ids ->
                    fetch { api.getKabadiwalaListings(ids.joinToString(",")).requireData() }
                        .onSuccess { listings -> if (accountId() == owner && generation == supplyRefreshGeneration) _state.value = _state.value.copy(listings = (_state.value.listings + listings).distinctBy { it.id }) }
                        .onFailure { error -> if (error !is CancellationException && accountId() == owner && generation == supplyRefreshGeneration) _state.value = _state.value.copy(error = friendly(error)) }
                }
            } finally {
                if (accountId() == owner) _state.value = _state.value.copy(busy = _state.value.busy - busyKey)
            }
        }
    }

    fun refreshRecycler() {
        if (!allowed(AccountRole.RECYCLER) || !protectedSessionReady()) return
        resetForAccountChange()
        if (supplyRefreshJob?.isActive == true) return
        load { current -> coroutineScope {
        val savedSupply = withContext(Dispatchers.IO) { supplySnapshotStore?.load(accountId(), "RECYCLER") }
        if (current() && savedSupply != null && _state.value.bulkLots.isEmpty() && _state.value.offers.isEmpty()) {
            _state.value = _state.value.copy(bulkLots = savedSupply.first.bulkLots, offers = savedSupply.first.offers, requirements = savedSupply.first.requirements, cachedAtEpochMs = savedSupply.second, showingCachedEvidence = true)
        }
        val cached = withContext(Dispatchers.IO) { cache?.load(accountId()) }
        if (current() && !_state.value.initialLoadComplete) cached?.let(::applyCached)
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (error: Exception) { if (error is CancellationException) throw error; partialFailure = true; fallback }
        val previous = _state.value
        val lotsRequest = async { optional(previous.bulkLots) { val response = api.getRecyclerBulkLots(limit = 50); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(recyclerLotsNextCursor = response.body()?.page?.nextCursor); rows }.also { if (current()) _state.value = _state.value.copy(bulkLots = it) } }
        val offersRequest = async { optional(previous.offers) { val response = api.getRecyclerBulkOffers(limit = 50); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(recyclerOffersNextCursor = response.body()?.page?.nextCursor); rows }.also { if (current()) _state.value = _state.value.copy(offers = it) } }
        val requirementsRequest = async { optional(previous.requirements) { val response = api.getRecyclerProcurementRequirements(limit = 50); val rows = response.requireData(); if (current()) _state.value = _state.value.copy(recyclerRequirementsNextCursor = response.body()?.page?.nextCursor); rows }.also { if (current()) _state.value = _state.value.copy(requirements = it) } }
        val poolsRequest = async { optional(previous.pools) { api.getRecyclerPools().requireData() } .also { if (current()) _state.value = _state.value.copy(pools = it) } }
        val handoversRequest = async { optional(previous.handovers) { api.getSupplyHandovers().requireData() } .also { if (current()) _state.value = _state.value.copy(handovers = it) } }
        val lots = lotsRequest.await()
        val offers = offersRequest.await()
        val requirements = requirementsRequest.await()
        val pools = poolsRequest.await()
        val handovers = handoversRequest.await()
        if (!current()) return@coroutineScope
        _state.value = _state.value.copy(loading = false, initialLoadComplete = !partialFailure || previous.initialLoadComplete, bulkLots = lots, offers = offers, requirements = requirements, pools = pools, handovers = handovers, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Could not load all current data. Check your connection and retry." else null)
        if (!partialFailure) {
            saveCache()
            withContext(Dispatchers.IO) { supplySnapshotStore?.save(accountId(), "RECYCLER", SupplySnapshot(bulkLots = lots, offers = offers, requirements = requirements)) }
        }
        } }
    }
    private suspend fun <T> fetch(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
    fun loadMoreRecyclerLots() {
        val cursor = _state.value.recyclerLotsNextCursor ?: return
        val generation = supplyRefreshGeneration
        action("more-recycler-lots", AccountRole.RECYCLER) {
            val owner = accountId()
            val response = api.getRecyclerBulkLots(limit = 50, cursor = cursor)
            val rows = response.requireData()
            if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.recyclerLotsNextCursor == cursor) {
                _state.value = _state.value.copy(bulkLots = (_state.value.bulkLots + rows).distinctBy { it.id }, recyclerLotsNextCursor = response.body()?.page?.nextCursor)
            }
            "Older lots loaded."
        }
    }
    fun loadMoreRecyclerOffers() {
        val cursor = _state.value.recyclerOffersNextCursor ?: return
        val generation = supplyRefreshGeneration
        action("more-recycler-offers", AccountRole.RECYCLER) {
            val owner = accountId()
            val response = api.getRecyclerBulkOffers(limit = 50, cursor = cursor)
            val rows = response.requireData()
            if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.recyclerOffersNextCursor == cursor) {
                _state.value = _state.value.copy(offers = (_state.value.offers + rows).distinctBy { it.id }, recyclerOffersNextCursor = response.body()?.page?.nextCursor)
            }
            "Older offers loaded."
        }
    }
    fun loadMoreRecyclerRequirements() {
        val cursor = _state.value.recyclerRequirementsNextCursor ?: return
        val generation = supplyRefreshGeneration
        action("more-recycler-demands", AccountRole.RECYCLER) {
            val owner = accountId()
            val response = api.getRecyclerProcurementRequirements(limit = 50, cursor = cursor)
            val rows = response.requireData()
            if (accountId() == owner && generation == supplyRefreshGeneration && _state.value.recyclerRequirementsNextCursor == cursor) {
                _state.value = _state.value.copy(requirements = (_state.value.requirements + rows).distinctBy { it.id }, recyclerRequirementsNextCursor = response.body()?.page?.nextCursor)
            }
            "Older demand loaded."
        }
    }

    private fun load(block: suspend (isCurrent: () -> Boolean) -> Unit) {
        val generation = ++supplyRefreshGeneration
        val refreshAccount = accountId()
        supplyRefreshJob?.cancel()
        lastRefreshStartedAt = System.currentTimeMillis()
        supplyRefreshJob = viewModelScope.launch {
            val isCurrent = { generation == supplyRefreshGeneration && accountId() == refreshAccount && protectedSessionReady() }
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                block(isCurrent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent()) _state.value = _state.value.copy(loading = false, error = friendly(error))
            }
        }
    }
    private fun action(key: String, requiredRole: AccountRole? = null, block: suspend () -> String = { "Done" }) {
        if (!protectedSessionReady()) {
            _state.value = _state.value.copy(error = "Your sign-in is no longer active. Sign in again, then retry.")
            return
        }
        if (requiredRole != null && !allowed(requiredRole)) {
            _state.value = _state.value.copy(error = "This action is not available for the signed-in account.")
            return
        }
        resetForAccountChange()
        if (key in _state.value.busy) return
        UiActionTrace.begin(key)
        if (requiredRole == AccountRole.HOUSEHOLD) {
            householdRefreshGeneration++
            householdRefreshJob?.cancel()
            householdRefreshJob = null
            pendingHouseholdRefresh = null
        }
        if (requiredRole == AccountRole.COLLECTOR || requiredRole == AccountRole.RECYCLER) {
            // A read started before this action must not repaint the old server
            // snapshot over its pending or newly confirmed local state.
            supplyRefreshGeneration++
            supplyRefreshJob?.cancel()
            supplyRefreshJob = null
        }
        _state.value = _state.value.copy(busy = _state.value.busy + key, error = null, notice = null)
        UiActionTrace.statePublished(key)
        val requestAccount = accountId()
        actionJobs[key] = viewModelScope.launch {
            var succeeded = false
            try {
                val notice = block()
                if (accountId() == requestAccount && protectedSessionReady()) _state.value = _state.value.copy(notice = notice)
                succeeded = true
                // Persist without blocking the visible confirmation. Serialize
                // writes so an older snapshot cannot replace newer confirmed data.
                viewModelScope.launch {
                    persistenceMutex.withLock {
                        if (accountId() == requestAccount && protectedSessionReady()) {
                            val visible = _state.value
                            val role = roleProvider()?.name ?: requiredRole?.name ?: return@withLock
                            runCatching {
                                saveCache()
                                withContext(Dispatchers.IO) {
                                    if (accountId() == requestAccount) supplySnapshotStore?.save(requestAccount, role,
                                        SupplySnapshot(visible.listings, visible.pickups, visible.inventory, visible.bulkLots, visible.offers, visible.requirements))
                                }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (accountId() == requestAccount && protectedSessionReady()) _state.value = _state.value.copy(error = friendly(error))
            } finally {
                UiActionTrace.finish(key, succeeded)
                actionJobs.remove(key)
                if (accountId() == requestAccount) {
                    _state.value = _state.value.copy(busy = _state.value.busy - key)
                    if (succeeded && protectedSessionReady() && requiredRole != null && key != "route-advantage" && key != "safety-routing" && !key.startsWith("receive-")) {
                        // A push/resume refresh may have started while this write
                        // was in flight. Discard it before reconciling the result.
                        householdRefreshGeneration++
                        householdRefreshJob?.cancel()
                        householdRefreshJob = null
                        pendingHouseholdRefresh = null
                        supplyRefreshGeneration++
                        supplyRefreshJob?.cancel()
                        supplyRefreshJob = null
                        when (requiredRole) {
                            AccountRole.HOUSEHOLD -> refreshHousehold()
                            AccountRole.COLLECTOR -> refreshKabadiwala()
                            AccountRole.RECYCLER -> refreshRecycler()
                            AccountRole.ADMIN -> Unit
                        }
                    }
                }
            }
        }
    }
    private suspend fun uploadListingPhotos(listingId: String, localPaths: List<String>) {
        require(localPaths.isNotEmpty()) { "Selected photo is no longer available" }
        val photos = localPaths.map { path ->
            val photo = File(path)
            check(photo.isFile) { "Selected photo is no longer available" }
            MultipartBody.Part.createFormData("photos", photo.name, photo.asRequestBody(photo.imageMimeType().toMediaTypeOrNull()))
        }
        api.uploadHouseholdListingPhotos(listingId, photos).requireData()
        localPaths.forEach { File(it).delete() }
    }
    fun createListing(input: HouseholdListingCreateDto, localPhotoPath: String? = null) =
        createListing(input, listOfNotNull(localPhotoPath), UUID.randomUUID().toString())
    fun createListing(input: HouseholdListingCreateDto, localPhotoPaths: List<String>) =
        createListing(input, localPhotoPaths, UUID.randomUUID().toString())
    fun createListing(input: HouseholdListingCreateDto, localPhotoPaths: List<String>, draftId: String) = action("create-listing", AccountRole.HOUSEHOLD, {
        require(localPhotoPaths.any { it.isNotBlank() && File(it).isFile }) { "Add at least one photo before posting." }
        val listingOwner = accountId()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in again before posting this listing.")
        val operation = "listing-${draftId.ifBlank { UUID.randomUUID().toString() }.take(112)}"
        val operationKey = idempotencyKeys?.getOrCreate(operation)
            ?: UUID.nameUUIDFromBytes(operation.toByteArray()).toString()
        val created = try {
            api.createHouseholdListing(input.copy(photoReference = null), operationKey).requireData()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (accountId() != listingOwner) throw CancellationException("Account changed while posting listing")
            val transient = error.isRetryableTransportFailure()
            // A definitive rejection means this draft will be corrected and
            // retried as a new request. Release its key so the retry cannot be
            // blocked by an old key bound to another payload.
            if (!transient) idempotencyKeys?.clear(operation)
            if (!transient || syncQueue == null) throw error
            val account = listingOwner
            val localListingId = "local-$operationKey"
            val localListing = HouseholdListingDto(
                id = localListingId, householdId = account,
                materialCategory = input.materialCategory, estimatedWeight = input.estimatedWeight,
                condition = input.condition, notes = input.notes, areaName = input.areaName,
                pickupAddress = input.pickupAddress, latitude = input.latitude, longitude = input.longitude,
                estimatedPriceMin = input.estimatedPriceMin, estimatedPriceMax = input.estimatedPriceMax,
                status = "PENDING_SYNC"
            ).toCacheEntity(account, synced = false)
            val payload = JsonObject().apply {
                add("input", com.google.gson.JsonParser.parseString(Gson().toJson(input.copy(photoReference = null))))
                addProperty("idempotencyKey", operationKey)
                addProperty("idempotencyOperation", operation)
                addProperty("localListingId", localListingId)
                val paths = localPhotoPaths.filter { it.isNotBlank() }
                if (paths.isNotEmpty()) {
                    add("photoPaths", com.google.gson.JsonArray().also { array -> paths.forEach(array::add) })
                    addProperty("photoPath", paths.first())
                }
            }
            val item = SyncQueueItemEntity(
                operation = "CREATE_HOUSEHOLD_LISTING", payloadJson = Gson().toJson(payload),
                createdAtEpochMs = System.currentTimeMillis(), accountId = account,
                idempotencyKey = operationKey
            )
            val persist = suspend {
                syncQueue.enqueueOnce(item)
                householdListingCache?.upsert(localListing)
            }
            if (localDatabase != null) localDatabase.withTransaction { persist() } else persist()
            _state.value = _state.value.copy(listings = listOf(localListing.toHouseholdListing()) + _state.value.listings.filterNot { it.id == localListingId })
            // Keep the key until the worker receives an applied response. A
            // repeated offline tap must replay the same server mutation, not
            // create a second listing with a fresh idempotency key.
            requestSync?.invoke()
            return@action "Listing saved offline and will post when connected."
        }
        if (accountId() != listingOwner) throw CancellationException("Account changed while posting listing")
        _state.value = _state.value.copy(listings = listOf(created) + _state.value.listings.filterNot { it.id == created.id })
        localPhotoPaths.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { paths ->
            val account = accountId()
            val pending = PendingPhotoUpload(created.id, paths)
            _state.value = _state.value.copy(pendingPhotoUpload = pending)
            if (account != null) {
                pendingPhotoUploads?.upsert(PendingPhotoUploadEntity(created.id, account, paths.first(), System.currentTimeMillis(), Gson().toJson(paths)))
            }
            try {
                uploadListingPhotos(created.id, paths)
                if (account != null) pendingPhotoUploads?.remove(account, created.id)
                _state.value = _state.value.copy(pendingPhotoUpload = null)
            } catch (error: Throwable) {
                refreshHousehold()
                throw error
            }
        }
        accountId()?.takeIf { it.isNotBlank() }?.let { account ->
            householdListingCache?.upsert(created.toCacheEntity(account, synced = true))
        }
        idempotencyKeys?.clear(operation)
        refreshHousehold()
        "Listing posted — choose a nearby Kabadiwala."
    })
    fun retryListingPhoto() = action("upload-listing-photo", AccountRole.HOUSEHOLD, {
        val pending = _state.value.pendingPhotoUpload ?: return@action "No photo upload needs retrying."
        uploadListingPhotos(pending.listingId, pending.localPaths)
        accountId()?.let { pendingPhotoUploads?.remove(it, pending.listingId) }
        _state.value = _state.value.copy(pendingPhotoUpload = null)
        refreshHousehold()
        "Photo uploaded securely."
    })
    fun requestPickup(listingId: String, kabadiwalaId: String? = null) = action("pickup-$listingId", AccountRole.HOUSEHOLD, {
        val queueAccount = accountId()?.takeIf { it.isNotBlank() }
        val alreadyPendingForListing = if (queueAccount != null && syncQueue != null) {
            syncQueue.observeForAccount(queueAccount).first().any { item ->
                item.operation == "REQUEST_HOUSEHOLD_PICKUP" && (item.lastErrorCode == null || item.attempts < 3) &&
                    runCatching { JsonParser.parseString(item.payloadJson).asJsonObject.get("listingId")?.asString == listingId }.getOrDefault(false)
            }
        } else false
        if (alreadyPendingForListing) {
            requestSync?.invoke()
            return@action "Pickup is already saved offline and will sync when connected."
        }
        val operation = "pickup-$listingId-${kabadiwalaId ?: "waiting"}"
        val key = idempotencyKeys?.getOrCreate(operation) ?: "pickup-$listingId-${kabadiwalaId ?: "waiting"}"
        try {
            publishPickup(api.requestHouseholdPickup(listingId, PickupRequestCreateDto(kabadiwalaId), key).requireData())
            idempotencyKeys?.clear(operation)
            refreshHousehold()
            if (kabadiwalaId == null) "Pickup saved. We’ll notify nearby Kabadiwalas." else "Pickup request sent."
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val transient = error.isRetryableTransportFailure()
            if (!transient || syncQueue == null) throw error
            val queueAccount = accountId()?.takeIf { it.isNotBlank() }
                ?: throw error
            val payload = com.google.gson.JsonObject().apply {
                addProperty("listingId", listingId)
                kabadiwalaId?.let { addProperty("kabadiwalaId", it) }
                addProperty("idempotencyKey", key)
                addProperty("idempotencyOperation", operation)
            }
            // The UI can be resumed and tapped again while the device is
            // offline. The stable idempotency key protects the server, but
            // the local outbox must also contain only one row for that
            // account/key pair so the worker does not replay duplicate work.
            val alreadyQueued = syncQueue.findUidByOperationAndIdempotencyKey(
                "REQUEST_HOUSEHOLD_PICKUP",
                queueAccount,
                key
            )
            if (alreadyQueued == null) {
                syncQueue.enqueueOnce(
                    SyncQueueItemEntity(
                        operation = "REQUEST_HOUSEHOLD_PICKUP",
                        payloadJson = Gson().toJson(payload),
                        createdAtEpochMs = System.currentTimeMillis(),
                        accountId = queueAccount,
                        idempotencyKey = key
                    )
                )
            }
            requestSync?.invoke()
            if (alreadyQueued == null) {
                "Pickup saved offline and will sync when connected."
            } else {
                "Pickup is already saved offline and will sync when connected."
            }
        }
    })
    fun loadHouseholdPickupQr(pickupId: String) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        _state.value = _state.value.copy(householdPickupQr = null, householdPickupQrLoadingId = pickupId, householdPickupQrError = null)
        viewModelScope.launch {
            runCatching { api.getHouseholdPickupQr(pickupId).requireData() }
                .onSuccess { qr ->
                    if (_state.value.householdPickupQrLoadingId == pickupId) {
                        _state.value = _state.value.copy(householdPickupQr = qr, householdPickupQrLoadingId = null, householdPickupQrError = null)
                    }
                }
                .onFailure { error ->
                    if (_state.value.householdPickupQrLoadingId == pickupId) {
                        _state.value = _state.value.copy(householdPickupQrLoadingId = null, householdPickupQrError = friendly(error))
                    }
                }
        }
    }
    fun clearHouseholdPickupQr() {
        _state.value = _state.value.copy(householdPickupQr = null, householdPickupQrLoadingId = null, householdPickupQrError = null)
    }
    fun verifyHouseholdPickupQr(pickupId: String, qrCodeData: String) = action("verify-household-qr-$pickupId", AccountRole.COLLECTOR, {
        val verified = api.confirmHouseholdPickupQr(pickupId, HouseholdPickupQrVerifyDto(qrCodeData)).requireData()
        _state.value = _state.value.copy(pickups = _state.value.pickups.map { if (it.id == verified.id) verified else it })
        refreshKabadiwala()
        "Household pickup verified. You can now record the final weight."
    })
    fun clearHouseholdMaterialSuggestion() {
        materialDetectionJob?.cancel()
        materialDetectionJob = null
        _state.value = _state.value.copy(materialSuggestion = null, materialDetectionPath = null, materialDetectionStatus = HouseholdMaterialDetectionStatus.IDLE, materialDetectionMessage = null)
    }
    fun suggestHouseholdMaterial(path: String) {
        if (path.isBlank()) {
            Log.w("HouseholdMaterialAI", "Detection skipped: photo path is blank")
            return
        }
        Log.d("HouseholdMaterialAI", "Detection requested; photo file exists=${File(path).isFile}")
        if (!allowed(AccountRole.HOUSEHOLD)) {
            Log.w("HouseholdMaterialAI", "Detection blocked: active account is not Household")
            _state.value = _state.value.copy(
                materialSuggestion = null,
                materialDetectionPath = path,
                materialDetectionStatus = HouseholdMaterialDetectionStatus.SERVICE_ERROR,
                materialDetectionMessage = "Photo detection is unavailable because the active account is not a Household account."
            )
            return
        }
        if (_state.value.materialDetectionStatus == HouseholdMaterialDetectionStatus.PROCESSING && _state.value.materialDetectionPath == path) return
        materialDetectionJob?.cancel()
        _state.value = _state.value.copy(materialSuggestion = null, materialDetectionPath = path, materialDetectionStatus = HouseholdMaterialDetectionStatus.PROCESSING, materialDetectionMessage = null)
        Log.d("HouseholdMaterialAI", "Detection entered processing state")
        materialDetectionJob = viewModelScope.launch {
            try {
                val source = File(path)
                // Image decoding, resizing, and JPEG compression are CPU and disk
                // work. Keep them off Main so the form can render its loading
                // state and continue accepting input while detection starts.
                val prepared = withContext(Dispatchers.IO) {
                    ImagePipeline.prepareForUpload(source, source.parentFile ?: File(System.getProperty("java.io.tmpdir").orEmpty()))
                }
                Log.d("HouseholdMaterialAI", "Photo prepared on IO (${prepared.length()} bytes); sending request")
                val suggestion = try {
                    val body = prepared.asRequestBody(prepared.imageMimeType().toMediaTypeOrNull())
                    val language = LocaleManager.toBackendName(languageProvider()).toRequestBody("text/plain".toMediaTypeOrNull())
                    api.suggestLotMaterial(MultipartBody.Part.createFormData("photo", prepared.name, body), language).requireData()
                } finally {
                    prepared.delete()
                }
                // OTHER is a valid high-confidence result for a whole phone,
                // tablet, camera, or an item outside the short material list.
                // Do not hide it just because the provider omitted itemName.
                val confident = suggestion.source.equals("AI", ignoreCase = true) && suggestion.confidence >= 0.6
                Log.d("HouseholdMaterialAI", "Detection completed: category=${suggestion.materialCategory}, confidence=${suggestion.confidence}")
                _state.value = _state.value.copy(materialSuggestion = suggestion, materialDetectionStatus = if (confident) HouseholdMaterialDetectionStatus.SUCCESS else HouseholdMaterialDetectionStatus.LOW_CONFIDENCE)
            } catch (error: IllegalArgumentException) {
                Log.w("HouseholdMaterialAI", "Photo preprocessing rejected the image (${error.javaClass.simpleName})")
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE, materialDetectionMessage = "This photo could not be processed. Choose a clear JPEG, PNG, or WebP image and try again.")
            } catch (error: RemoteApiException) {
                Log.w("HouseholdMaterialAI", "Detection request failed (http=${error.httpCode}, code=${error.code})")
                val serviceFailure = error.httpCode == null || error.httpCode >= 500 || error.code == "SERVICE_UNAVAILABLE" || error.code == "GEMINI_UNAVAILABLE"
                val status = when { error.httpCode == 422 || error.code == "VALIDATION_ERROR" -> HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE; serviceFailure -> HouseholdMaterialDetectionStatus.SERVICE_ERROR; else -> HouseholdMaterialDetectionStatus.NETWORK_ERROR }
                val message = when {
                    status == HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE -> "This photo could not be read. Choose a clear JPEG, PNG, or WebP image and try again."
                    error.httpCode == 401 -> "Your session expired. Sign in again, then retry photo detection."
                    error.httpCode == 403 -> "Photo detection is unavailable for this account. Choose the material below."
                    error.httpCode == 429 -> "Photo detection is busy. Wait a moment and retry."
                    serviceFailure -> "The AI service is temporarily unavailable. You can choose the material below or retry."
                    else -> "Photo detection could not connect. Check your connection and retry."
                }
                _state.value = _state.value.copy(materialDetectionStatus = status, materialDetectionMessage = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                Log.w("HouseholdMaterialAI", "Detection network error (${error.javaClass.simpleName})")
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.NETWORK_ERROR, materialDetectionMessage = "Photo detection could not connect. Check your connection and retry.")
            } catch (error: Exception) {
                Log.e("HouseholdMaterialAI", "Unexpected detection failure (${error.javaClass.simpleName})")
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.SERVICE_ERROR, materialDetectionMessage = "Photo detection failed unexpectedly. You can choose the material below or retry.")
            }
        }
    }

    fun clearHouseholdPriceEstimate() {
        householdPriceEstimateJob?.cancel()
        householdPriceEstimateJob = null
        _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = null)
    }

    fun estimateHouseholdPrice(materialCategory: String, weightKg: Double, condition: String, areaName: String) {
        if (!allowed(AccountRole.HOUSEHOLD)) {
            _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = "Price estimates are available for Household accounts.")
            return
        }
        if (!protectedSessionReady()) {
            _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = "Your session is still being restored. Try the price estimate again in a moment.")
            return
        }
        if (!weightKg.isFinite() || weightKg <= 0.0 || weightKg > 500.0) {
            clearHouseholdPriceEstimate()
            return
        }
        householdPriceEstimateJob?.cancel()
        val requestedArea = areaName.trim()
        _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = true, householdPriceEstimateMessage = null)
        householdPriceEstimateJob = viewModelScope.launch {
            delay(350)
            try {
                val board = api.getPriceBoard(materialCategory, requestedArea.ifBlank { null }).requireData()
                val priceMin = board.priceMin
                val priceMax = board.priceMax
                if (!board.available || priceMin == null || priceMax == null || priceMin <= 0.0 || priceMax < priceMin) {
                    _state.value = _state.value.copy(
                        householdPriceEstimate = null,
                        householdPriceEstimateLoading = false,
                        householdPriceEstimateMessage = "No current verified price range is available for this material and area. Your Kabadiwala will confirm the price after weighing."
                    )
                    return@launch
                }
                val conditionMultiplier = when (condition) { "DAMAGED" -> 0.7; "PARTIAL" -> 0.4; else -> 1.0 }
                val estimate = HouseholdPriceEstimate(
                    materialCategory = materialCategory,
                    weightKg = weightKg,
                    condition = condition,
                    areaName = requestedArea,
                    minimum = (priceMin * weightKg * conditionMultiplier).roundMoney(),
                    maximum = (priceMax * weightKg * conditionMultiplier).roundMoney(),
                    disclaimer = board.disclaimer ?: "Indicative range only. The Kabadiwala confirms the final price after inspection."
                )
                _state.value = _state.value.copy(householdPriceEstimate = estimate, householdPriceEstimateLoading = false, householdPriceEstimateMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = "Couldn't load current prices. You can still post the listing; the final price is confirmed after weighing.")
            } catch (error: RemoteApiException) {
                _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = when (error.httpCode) {
                    401 -> "Your session expired. Sign in again to load the price estimate."
                    403 -> "Price estimates are unavailable for this account. You can still post the listing."
                    else -> "Couldn't load current prices. You can still post the listing; the final price is confirmed after weighing."
                })
            } catch (_: Exception) {
                _state.value = _state.value.copy(householdPriceEstimate = null, householdPriceEstimateLoading = false, householdPriceEstimateMessage = "Couldn't calculate a price range right now. You can still post the listing.")
            }
        }
    }

    private fun Double.roundMoney(): Double = kotlin.math.round(this * 100.0) / 100.0

    fun loadKabadiwalaListingPhotos(listingId: String, photoCount: Int) = loadListingPhotos(listingId, photoCount, AccountRole.COLLECTOR)

    fun loadHouseholdListingPhotos(listingId: String, photoCount: Int) = loadListingPhotos(listingId, photoCount, AccountRole.HOUSEHOLD)

    private fun loadListingPhotos(listingId: String, photoCount: Int, ownerRole: AccountRole) {
        if (!protectedSessionReady() || !allowed(ownerRole)) {
            val message = if (!protectedSessionReady()) {
                "Your sign-in is no longer active. Sign in again to view these photos."
            } else {
                "Only the listing owner or assigned Kabadiwala can view these photos."
            }
            _state.value = _state.value.copy(listingPhotoErrors = _state.value.listingPhotoErrors + (listingId to message))
            return
        }
        resetForAccountChange()
        val count = photoCount.coerceIn(1, 6)
        if (_state.value.listingPhotos[listingId].orEmpty().size >= count && listingId !in _state.value.listingPhotoErrors) return
        val key = "photos-$listingId"
        if (key in _state.value.busy) return
        val requestAccount = accountId()
        _state.value = _state.value.copy(
            busy = _state.value.busy + key,
            listingPhotoErrors = _state.value.listingPhotoErrors - listingId
        )
        viewModelScope.launch {
            try {
                val results = coroutineScope {
                    (0 until count).map { index ->
                        async {
                            try {
                                val response = if (ownerRole == AccountRole.HOUSEHOLD) {
                                    if (index == 0) api.getHouseholdListingPhoto(listingId) else api.getHouseholdListingPhotoAtIndex(listingId, index)
                                } else {
                                    if (index == 0) api.getKabadiwalaListingPhoto(listingId) else api.getKabadiwalaListingPhotoAtIndex(listingId, index)
                                }
                                val bytes = withContext(Dispatchers.IO) {
                                    val downloaded = response.requireBody().bytes()
                                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeByteArray(downloaded, 0, downloaded.size, bounds)
                                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "The server returned an unreadable photo." }
                                    downloaded
                                }
                                Result.success(bytes)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }
                        }
                    }.awaitAll()
                }
                if (accountId() != requestAccount || !protectedSessionReady() || !allowed(ownerRole)) return@launch
                val photos = results.mapNotNull { it.getOrNull() }
                val firstFailure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
                val photoError = when {
                    firstFailure == null -> null
                    firstFailure is IOException -> "Photo download was interrupted. Retry when your connection is stable."
                    firstFailure is IllegalStateException -> firstFailure.message ?: "The server returned an unreadable photo."
                    else -> "Couldn't load the scrap photo. ${friendly(firstFailure)}"
                }
                if (photoError == null) Log.d("CollectorListingPhoto", "Loaded $count photo(s) for listing $listingId")
                else Log.w("CollectorListingPhoto", "Loaded ${photos.size}/$count photo(s) for listing $listingId: ${firstFailure?.javaClass?.simpleName}")
                _state.value = _state.value.copy(
                    listingPhotos = if (photos.isEmpty()) _state.value.listingPhotos else _state.value.listingPhotos + (listingId to photos),
                    listingPhotoErrors = if (photoError == null) _state.value.listingPhotoErrors - listingId
                    else _state.value.listingPhotoErrors + (listingId to photoError)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("CollectorListingPhoto", "Failed loading listing $listingId", error)
                if (accountId() == requestAccount) {
                    _state.value = _state.value.copy(
                        listingPhotoErrors = _state.value.listingPhotoErrors +
                            (listingId to "Couldn't display the scrap photo. ${friendly(error)}")
                    )
                }
            } finally {
                if (accountId() == requestAccount) {
                    _state.value = _state.value.copy(busy = _state.value.busy - key)
                }
            }
        }
    }
    private fun publishPickupSection(rows: List<PickupRequestDto>, scope: String) {
        fun belongs(p: PickupRequestDto) = when (scope) {
            "history" -> p.status in terminalCollectorPickupStatuses
            "waiting" -> p.kabadiwalaId == null && p.status == "WAITING_FOR_PICKUP"
            else -> p.kabadiwalaId != null && p.status !in terminalCollectorPickupStatuses
        }
        _state.value = _state.value.copy(pickups = (rows + _state.value.pickups.filterNot(::belongs)).distinctBy { it.id })
    }

    private fun publishPickup(pickup: PickupRequestDto) {
        val previous = _state.value.pickups.firstOrNull { it.id == pickup.id }
        val confirmed = pickup.copy(settlementPayment = pickup.settlementPayment ?: previous?.settlementPayment)
        _state.value = _state.value.copy(
            pickups = listOf(confirmed) + _state.value.pickups.filterNot { it.id == pickup.id },
            listings = _state.value.listings.map { listing ->
                if (listing.id == pickup.listingId && pickup.status == "COMPLETED") listing.copy(status = "COMPLETED") else listing
            }
        )
    }

    private fun publishPayment(pickupId: String, payment: PickupSettlementPaymentDto) {
        _state.value = _state.value.copy(pickups = _state.value.pickups.map {
            if (it.id == pickupId) it.copy(settlementPayment = payment) else it
        })
    }

    fun cancelListing(listingId: String, reason: String? = null) = action("cancel-listing-$listingId", AccountRole.HOUSEHOLD, { api.cancelHouseholdListing(listingId, CancellationRequestDto(reason)).requireSuccess(); _state.value = _state.value.copy(listings = _state.value.listings.map { if (it.id == listingId) it.copy(status = "CANCELLED") else it }); "Listing cancelled." })
    fun cancelPickup(pickupId: String, reason: String? = null) = action("cancel-pickup-$pickupId", AccountRole.HOUSEHOLD, { api.cancelHouseholdPickup(pickupId, CancellationRequestDto(reason)).requireSuccess(); _state.value = _state.value.copy(pickups = _state.value.pickups.map { if (it.id == pickupId) it.copy(status = "CANCELLED") else it }); "Pickup cancelled." })
    fun reschedulePickup(pickupId: String, scheduledSlot: String) = action("reschedule-$pickupId", AccountRole.HOUSEHOLD, { publishPickup(api.rescheduleHouseholdPickup(pickupId, PickupRescheduleDto(scheduledSlot)).requireData()); "Pickup rescheduled." })
    fun decideHouseholdSettlement(pickupId: String, decision: String, reasonCode: String? = null, notes: String? = null) = action("settlement-$pickupId", AccountRole.HOUSEHOLD, { publishPickup(api.decideHouseholdSettlement(pickupId, SettlementDecisionDto(decision, reasonCode, null, notes)).requireData()); "Settlement decision recorded." })
    fun confirmHouseholdPaymentReceived(pickupId: String) = action("payment-received-$pickupId", AccountRole.HOUSEHOLD, { publishPayment(pickupId, api.confirmHouseholdPaymentReceived(pickupId).requireData()); "Payment receipt confirmed." })
    fun acceptListing(listingId: String) = action("pickup-decision-${_state.value.pickups.firstOrNull { it.listingId == listingId }?.id ?: listingId}", AccountRole.COLLECTOR, {
        try {
            val requestId = UUID.randomUUID().toString()
            UiActionTrace.requestLinked("pickup-decision-${_state.value.pickups.firstOrNull { it.listingId == listingId }?.id ?: listingId}", requestId)
            api.acceptHouseholdListing(listingId, requestId).requireSuccess()
            val acceptedAt = java.time.Instant.now().toString()
            _state.value = _state.value.copy(
                pickups = _state.value.pickups.map { pickup ->
                    if (pickup.listingId == listingId) pickup.copy(status = "ACCEPTED", acceptedAt = acceptedAt, updatedAt = acceptedAt) else pickup
                }
            )
            "Pickup accepted."
        } catch (error: Throwable) {
            if ((error as? RemoteApiException)?.let { it.code == "PICKUP_NOT_AVAILABLE" || it.message.contains("no longer available", ignoreCase = true) || it.message.contains("not available to accept", ignoreCase = true) } == true) {
                    "Another Kabadiwala already took this pickup. The queue is refreshing."
            } else throw error
        }
    })
    fun rejectPickup(pickupId: String, reason: String? = null) = action("pickup-decision-$pickupId", AccountRole.COLLECTOR, {
        api.rejectKabadiwalaPickup(pickupId, BulkOfferDecisionDto(reason)).requireSuccess()
        _state.value = _state.value.copy(pickups = _state.value.pickups.filterNot { it.id == pickupId })
        "Pickup declined and returned to the network."
    })
    fun confirmAvailability(pickupId: String, slot: String? = null) = action("availability-$pickupId", AccountRole.COLLECTOR, {
        val updated = api.confirmPickupAvailability(pickupId, PickupAvailabilityDto(true, slot)).requireData()
        _state.value = _state.value.copy(
            pickups = _state.value.pickups.map { pickup -> if (pickup.id == pickupId) updated else pickup }
        )
        "Availability confirmed."
    })
    fun schedulePickup(pickupId: String, iso: String) = action("schedule-$pickupId", AccountRole.COLLECTOR, {
        api.schedulePickup(pickupId, PickupScheduleDto(iso)).requireSuccess()
        val changedAt = java.time.Instant.now().toString()
        _state.value = _state.value.copy(
            pickups = _state.value.pickups.map { pickup ->
                if (pickup.id == pickupId) pickup.copy(status = "SCHEDULED", scheduledSlot = iso, updatedAt = changedAt) else pickup
            }
        )
        "Pickup scheduled."
    })
    fun pickupStatus(pickupId: String, status: String) = action("status-$pickupId", AccountRole.COLLECTOR, {
        api.updatePickupStatus(pickupId, PickupStatusDto(status)).requireSuccess()
        val changedAt = java.time.Instant.now().toString()
        _state.value = _state.value.copy(
            pickups = _state.value.pickups.map { pickup ->
                if (pickup.id != pickupId) pickup else pickup.copy(
                    status = status,
                    updatedAt = changedAt,
                    inTransitAt = if (status == "IN_TRANSIT") changedAt else pickup.inTransitAt,
                    arrivedAt = if (status == "ARRIVED") changedAt else pickup.arrivedAt
                )
            }
        )
        "Pickup updated."
    })
    fun cancelKabadiwalaPickup(pickupId: String, reason: String? = null) = action("cancel-collector-$pickupId", AccountRole.COLLECTOR, { api.cancelKabadiwalaPickup(pickupId, CancellationRequestDto(reason)).requireSuccess(); _state.value = _state.value.copy(pickups = _state.value.pickups.map { if (it.id == pickupId) it.copy(status = "CANCELLED") else it }); "Pickup cancelled." })
    fun reassignPickup(pickupId: String, reason: String, noShow: Boolean = false) = action("reassign-$pickupId", AccountRole.COLLECTOR, { api.reassignPickup(pickupId, PickupReassignDto(reason, noShow)).requireData(); _state.value = _state.value.copy(pickups = _state.value.pickups.map { if (it.id == pickupId) it.copy(status = "REASSIGNMENT_REQUIRED", reassignmentReason = reason, noShow = noShow) else it }); "Pickup returned to the network for reassignment." })
    fun completePickup(pickupId: String, input: PickupCompletionDto) = action("complete-$pickupId", AccountRole.COLLECTOR, { publishPickup(api.completePickup(pickupId, input).requireData()); "Purchase completed and inventory updated." })
    fun recordPickupSettlementPayment(pickupId: String, input: PickupSettlementPaymentRequestDto) = action("pickup-payment-$pickupId", AccountRole.COLLECTOR, {
        publishPayment(pickupId, api.recordPickupSettlementPayment(pickupId, input, UUID.randomUUID().toString()).requireData())
        "Payment recorded for operator reconciliation."
    })
    fun rateHouseholdPickup(pickupId: String, rating: Int) = action("rate-pickup-$pickupId", AccountRole.HOUSEHOLD, {
        api.reviewHouseholdPickup(pickupId, HouseholdPickupReviewDto(rating)).requireData()
        _state.value.selectedKabadiwalaId?.let(::openKabadiwalaProfile)
        "Verified pickup rating submitted."
    })
    fun createBulkLot(input: BulkLotCreateDto) = action("create-bulk", AccountRole.COLLECTOR) {
        // Keep the returned server record visible immediately. The follow-up
        // refresh reconciles it with the authoritative list, but a slow or
        // partially unavailable catalogue must not make a successful lot look
        // as if it disappeared.
        val created = api.createBulkLot(input).requireData()
        _state.value = _state.value.copy(bulkLots = listOf(created) + _state.value.bulkLots.filterNot { it.id == created.id })
        "Bulk lot listed for verified recyclers."
    }
    fun cancelBulkLot(lotId: String) = action("cancel-bulk-$lotId", AccountRole.COLLECTOR, { api.cancelBulkLot(lotId).requireSuccess(); _state.value = _state.value.copy(bulkLots = _state.value.bulkLots.map { if (it.id == lotId) it.copy(status = "CANCELLED") else it }, offers = _state.value.offers.map { if (it.bulkLotId == lotId && it.status == "PENDING") it.copy(status = "CANCELLED") else it }); "Bulk lot cancelled and stock released." })
    fun acceptOffer(offerId: String) = action("offer-$offerId", AccountRole.COLLECTOR, {
        val owner = accountId()
        api.acceptBulkOffer(offerId).requireSuccess()
        if (owner == accountId() && protectedSessionReady()) {
            val offer = _state.value.offers.firstOrNull { it.id == offerId }
            _state.value = _state.value.copy(
                offers = _state.value.offers.map { if (it.id == offerId) it.copy(status = "ACCEPTED") else if (it.bulkLotId == offer?.bulkLotId && it.status == "PENDING") it.copy(status = "REJECTED") else it },
                bulkLots = _state.value.bulkLots.map { if (it.id == offer?.bulkLotId) it.copy(status = "RESERVED", reservedForId = offer.recyclerId) else it }
            )
        }
        "Recycler offer accepted; stock remains reserved."
    })
    fun rejectOffer(offerId: String, reason: String) = action("reject-offer-$offerId", AccountRole.COLLECTOR, { val updated = api.rejectBulkOffer(offerId, BulkOfferDecisionDto(reason.ifBlank { null })).requireData(); _state.value = _state.value.copy(offers = _state.value.offers.map { if (it.id == offerId) updated.copy(bulkLot = it.bulkLot, recyclerName = it.recyclerName) else it }); "Offer rejected." })
    fun counterOffer(offerId: String, rate: Double, notes: String?) = action("counter-offer-$offerId", AccountRole.COLLECTOR, { val updated = api.counterBulkOffer(offerId, BulkOfferCounterDto(rate, notes?.ifBlank { null })).requireData(); _state.value = _state.value.copy(offers = _state.value.offers.map { if (it.id == offerId) updated.copy(bulkLot = it.bulkLot, recyclerName = it.recyclerName) else it }); "Counter-offer sent." })
    fun makeOffer(lotId: String, rate: Double) = action("offer-$lotId", AccountRole.RECYCLER, {
        val owner = accountId()
        val offer = api.makeBulkLotOffer(lotId, BulkOfferCreateDto(rate)).requireData()
        if (owner == accountId() && protectedSessionReady()) {
            val previous = _state.value.offers.firstOrNull { it.id == offer.id }
            val visible = offer.copy(bulkLot = _state.value.bulkLots.firstOrNull { it.id == lotId } ?: previous?.bulkLot, recyclerName = previous?.recyclerName)
            _state.value = _state.value.copy(offers = listOf(visible) + _state.value.offers.filterNot { it.id == offer.id })
        }
        "Offer sent to the Kabadiwala."
    })
    fun withdrawOffer(offerId: String, reason: String?) = action("withdraw-offer-$offerId", AccountRole.RECYCLER, {
        val owner = accountId()
        val offer = api.withdrawRecyclerOffer(offerId, BulkOfferDecisionDto(reason?.ifBlank { null })).requireData()
        if (owner == accountId() && protectedSessionReady()) {
            _state.value = _state.value.copy(offers = _state.value.offers.map { if (it.id == offer.id) it.copy(status = offer.status, updatedAt = offer.updatedAt) else it })
        }
        "Offer withdrawn. You can submit a new offer while the lot is listed."
    })
    fun updateRequirement(requirementId: String, input: ProcurementRequirementUpdateDto) = action("update-demand-$requirementId", AccountRole.RECYCLER, { val updated = api.updateProcurementRequirement(requirementId, input).requireData(); _state.value = _state.value.copy(requirements = listOf(updated) + _state.value.requirements.filterNot { it.id == updated.id }); "Procurement requirement updated." })
    /**
     * Receiving is intentionally not implemented through the legacy
     * /recycler/bulk-lots/:lotId/receive compatibility guard. The production
     * workflow is the signed formal handover scanner and confirmation form.
     */
    fun receiveLot(lotId: String) = action("receive-$lotId", AccountRole.RECYCLER, { "Scan the signed handover QR to confirm receipt." })
    fun createRequirement(input: ProcurementRequirementCreateDto) = action("create-demand", AccountRole.RECYCLER, { val updated = api.createProcurementRequirement(input).requireData(); _state.value = _state.value.copy(requirements = listOf(updated) + _state.value.requirements.filterNot { it.id == updated.id }); "Requirement published to Kabadiwalas." })
    fun loadRouteAdvantage(materialCategory: String, quantityKg: Double, grade: String = "UNSPECIFIED") = action("route-advantage", AccountRole.COLLECTOR) {
        val result = api.getRouteAdvantage(materialCategory, quantityKg, grade).requireData()
        _state.value = _state.value.copy(routeAdvantage = result, showingCachedEvidence = false, cachedAtEpochMs = System.currentTimeMillis())
        saveCache()
        if (result.baseline == null) "Verified routes found, but there is not enough baseline data to claim savings." else "Route estimate ready. Confirm logistics and rate at handover."
    }
    fun loadSafetyRouting(materialCategory: String, condition: String) = action("safety-routing", AccountRole.COLLECTOR) {
        val result = api.getSafetyRouting(materialCategory, condition).requireData()
        _state.value = _state.value.copy(safetyRouting = result)
        "Safety route loaded. Follow the handling instruction before transport."
    }

    fun confirmSupplyPayment(handoverId: String, decision: String, reason: String?) = action("supply-payment-$handoverId", AccountRole.COLLECTOR) {
        val payment = api.confirmSupplyPayment(handoverId, SettlementDecisionDto(decision = decision, reasonCode = reason)).requireData()
        _state.value = _state.value.copy(handovers = _state.value.handovers.map { h ->
            if (h.id == handoverId) h.copy(payments = listOf(payment) + h.payments.filterNot { it.id == payment.id }) else h
        })
        "Payment status updated."
    }

    fun loadMaterialPassport(handoverId: String) = action("passport-$handoverId") {
        val result = api.getMaterialPassport(handoverId).requireData()
        _state.value = _state.value.copy(materialPassports = _state.value.materialPassports + (handoverId to result))
        "Material passport loaded."
    }
    fun loadAnomalies(handoverId: String) = action("anomalies-$handoverId") {
        val result = api.getHandoverAnomalies(handoverId).requireData()
        _state.value = _state.value.copy(anomalies = _state.value.anomalies + (handoverId to result))
        "Settlement risk review loaded."
    }
    fun decideSupplySettlement(handoverId: String, decision: String, reasonCode: String? = null, evidenceReference: String? = null, notes: String? = null) = action("supply-settlement-$handoverId") {
        val updated = api.decideSupplySettlement(handoverId, SettlementDecisionDto(decision, reasonCode, evidenceReference, notes)).requireData()
        _state.value = _state.value.copy(handovers = listOf(updated) + _state.value.handovers.filterNot { it.id == updated.id })
        saveCache()
        "Settlement decision recorded."
    }
    fun createPool(requirementId: String, areaName: String) = action("pool-create-$requirementId", AccountRole.COLLECTOR) { val pool = api.createPool(PoolCreateRequestDto(requirementId, areaName)).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); "Cooperative pool opened. Other Kabadiwalas can contribute reserved stock." }
    fun joinPool(poolId: String, quantityKg: Double, grade: String, expectedRatePerKg: Double?) = action("pool-join-$poolId", AccountRole.COLLECTOR) { api.joinPool(poolId, PoolJoinRequestDto(quantityKg, grade, expectedRatePerKg)).requireData(); "Stock reserved in the cooperative pool." }
    fun leavePool(poolId: String) = action("pool-leave-$poolId", AccountRole.COLLECTOR) { api.leavePool(poolId).requireData(); "Contribution released back to available stock." }
    fun lockPool(poolId: String) = action("pool-lock-$poolId", AccountRole.COLLECTOR) { val pool = api.lockPool(poolId).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); "Pool locked at threshold. Prepare the one-time handover QR." }
    fun preparePoolHandover(poolId: String) = action("handover-pool-$poolId", AccountRole.COLLECTOR) { val handover = api.preparePoolHandover(poolId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id || (it.poolId == poolId && it.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
    fun prepareBulkHandover(lotId: String) = action("handover-bulk-$lotId", AccountRole.COLLECTOR) { val handover = api.prepareBulkHandover(lotId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id || (it.bulkLotId == lotId && it.status in setOf("PREPARED", "COLLECTOR_CONFIRMED")) }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
    fun confirmCollectorHandover(handoverId: String) = action("collector-confirm-$handoverId", AccountRole.COLLECTOR) {
        val operation = "collector-handover-$handoverId"
        val handover = api.confirmCollectorHandover(handoverId, idempotencyKeys?.getOrCreate(operation)).requireData()
        idempotencyKeys?.clear(operation)
        _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id })
        saveCache()
        "Collector confirmation recorded. Recycler must scan this QR."
    }
    fun acknowledgeSafety(moduleKey: String) = action("safety-$moduleKey", AccountRole.COLLECTOR) { api.acknowledgeSafety(moduleKey).requireData(); val safety = api.getSafety().requireData(); _state.value = _state.value.copy(safety = safety); saveCache(); "Safety acknowledgement saved to your growth passport." }
}

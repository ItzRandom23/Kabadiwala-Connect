package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.gson.JsonObject
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationCacheStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.FormalisationSnapshot
import com.irinteractivestudios.kabadiwalaconnect.data.local.HouseholdListingCacheDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.toCacheEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.toHouseholdListing
import com.irinteractivestudios.kabadiwalaconnect.data.local.IdempotencyKeyStore
import com.irinteractivestudios.kabadiwalaconnect.data.local.PendingPhotoUploadDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.PendingPhotoUploadEntity
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueDao
import com.irinteractivestudios.kabadiwalaconnect.data.local.SyncQueueItemEntity
import com.irinteractivestudios.kabadiwalaconnect.data.remote.*
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole
import com.irinteractivestudios.kabadiwalaconnect.util.ImagePipeline
import com.irinteractivestudios.kabadiwalaconnect.util.LocaleManager
import com.irinteractivestudios.kabadiwalaconnect.util.CurrentLocation
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.util.UUID

data class SupplyChainState(
    val loading: Boolean = false,
    val error: String? = null,
    val listings: List<HouseholdListingDto> = emptyList(),
    val kabadiwalas: List<KabadiwalaProfileDto> = emptyList(),
    val kabadiwalaRadiusKm: Int = 25,
    val kabadiwalaAreaQuery: String = "",
    val kabadiwalaLatitude: Double? = null,
    val kabadiwalaLongitude: Double? = null,
    val kabadiwalaPage: Int = 1,
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
    val inventory: List<InventoryBalanceDto> = emptyList(),
    val inventoryMovements: List<InventoryMovementDto> = emptyList(),
    val bulkLots: List<BulkLotDto> = emptyList(),
    val offers: List<BulkOfferDto> = emptyList(),
    val requirements: List<ProcurementRequirementDto> = emptyList(),
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
    val materialDetectionStatus: HouseholdMaterialDetectionStatus = HouseholdMaterialDetectionStatus.IDLE,
    val materialDetectionMessage: String? = null,
    val listingPhotos: Map<String, List<ByteArray>> = emptyMap(),
    val listingPhotoErrors: Map<String, String> = emptyMap()
)

enum class HouseholdMaterialDetectionStatus { IDLE, PROCESSING, SUCCESS, LOW_CONFIDENCE, UNSUPPORTED_IMAGE, NETWORK_ERROR, SERVICE_ERROR }

data class PendingPhotoUpload(val listingId: String, val localPaths: List<String>)

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
    private val initialHouseholdArea: () -> String? = { null }
) : ViewModel() {
    private val _state = MutableStateFlow(SupplyChainState(kabadiwalaAreaQuery = initialHouseholdArea().orEmpty()))
    val state: StateFlow<SupplyChainState> = _state.asStateFlow()
    private var stateAccountId: String? = null
    private var householdRefreshGeneration = 0L

    private fun friendly(error: Throwable): String {
        val remote = error as? RemoteApiException
        val message = remote?.message.orEmpty()
        val code = remote?.code.orEmpty()
        return when {
            message.contains("no longer available", ignoreCase = true) || message.contains("not available to accept", ignoreCase = true) -> "Another Kabadiwala already took this pickup. The queue has been refreshed."
            message.contains("household QR", ignoreCase = true) && message.contains("before", ignoreCase = true) -> "Ask the household to show its pickup QR, then scan it before weighing."
            message.contains("QR is invalid or expired", ignoreCase = true) -> "This QR is invalid or expired. Ask the household to refresh it and scan again."
            message.contains("reason code is required", ignoreCase = true) -> "Choose why the final material, weight, or value differs from the listing."
            message.contains("already been scanned", ignoreCase = true) -> "This pickup was already verified. Refresh the pickup list to see the update."
            code in setOf("HTTP_401", "AUTHENTICATION_REQUIRED", "TOKEN_EXPIRED") || remote?.httpCode == 401 -> "Your session expired. Please sign in again."
            remote?.code == "HTTP_403" || remote?.httpCode == 403 -> "This action is not available for your role."
            code == "EMPTY_RESPONSE" -> "The server returned an incomplete response. Please try again."
            code in setOf("INVALID_PHOTO", "PHOTO_REQUIRED", "PHOTO_UPLOAD_FAILED") -> "That photo could not be uploaded. Choose another clear image and retry."
            remote?.httpCode == 409 -> "That record changed. Refresh and try again."
            remote?.httpCode == 422 -> "Check the highlighted details and try again."
            error is IllegalStateException && error.message?.contains("photo", ignoreCase = true) == true -> "The selected photo is no longer available. Choose it again."
            error is IOException -> "Connection issue. Check your internet and retry."
            else -> "Could not load the latest collection data. Please try again."
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

    /** A ViewModel can outlive a logout/account switch while its nav entry is
     * still retained. Never let account-scoped photos, errors, or lists bleed
     * into the next authenticated identity. */
    private fun resetForAccountChange() {
        val current = accountId()?.takeIf { it.isNotBlank() }
        if (current == stateAccountId) return
        stateAccountId = current
        _state.value = SupplyChainState(kabadiwalaAreaQuery = initialHouseholdArea().orEmpty())
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
        val pending = local.filter { cached -> cached.id !in remote.map { it.id }.toSet() && cached.status == "PENDING_SYNC" }
        cache.upsertAll(remote.map { it.toCacheEntity(account, synced = true) })
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
            pools = snapshot.pools,
            bulkLots = if (_state.value.bulkLots.isEmpty()) snapshot.bulkLots else _state.value.bulkLots,
            offers = if (_state.value.offers.isEmpty()) snapshot.offers else _state.value.offers,
            handovers = snapshot.handovers,
            passport = snapshot.passport,
            safety = snapshot.safety,
            showingCachedEvidence = true,
            cachedAtEpochMs = snapshot.cachedAtEpochMs
        )
    }

    private fun saveCache() {
        cache?.save(accountId(), FormalisationSnapshot(
            routeAdvantage = _state.value.routeAdvantage,
            poolOpportunities = _state.value.poolOpportunities,
            pools = _state.value.pools,
            bulkLots = _state.value.bulkLots,
            offers = _state.value.offers,
            handovers = _state.value.handovers,
            passport = _state.value.passport,
            safety = _state.value.safety
        ))
    }

    fun refreshHousehold(radiusKm: Int? = null, areaQuery: String? = null) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        resetForAccountChange()
        val generation = ++householdRefreshGeneration
        val requestedRadiusKm = radiusKm ?: _state.value.kabadiwalaRadiusKm
        val requestedArea = areaQuery ?: _state.value.kabadiwalaAreaQuery
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            val cached = runCatching { cachedHouseholdListings() }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                _state.value = _state.value.copy(listings = cached)
            }
            var activeSection = "your listings"
            runCatching {
                restorePendingPhotoUpload()
                val listings = mergeHouseholdListings(api.getHouseholdListings().requireData())
                activeSection = "your pickup history"
                val pickups = api.getHouseholdPickups().requireData()
                activeSection = "nearby Kabadiwalas"
                val directory = api.getHouseholdKabadiwalas(
                    _state.value.kabadiwalaLatitude,
                    _state.value.kabadiwalaLongitude,
                    requestedRadiusKm,
                    requestedArea.ifBlank { null }
                ).requireData().toKabadiwalaDirectoryDto()
                if (generation != householdRefreshGeneration) return@launch
                _state.value = _state.value.copy(loading = false, listings = listings, pickups = pickups, kabadiwalas = directory.items, kabadiwalaRadiusKm = requestedRadiusKm, kabadiwalaAreaQuery = requestedArea, kabadiwalaPage = directory.pagination.page, kabadiwalaHasMore = directory.pagination.page < directory.pagination.totalPages, kabadiwalaRequiresLocation = directory.requiresLocation, error = null)
            }.onFailure { error ->
                if (generation != householdRefreshGeneration) return@onFailure
                Log.e("HouseholdRefresh", "Failed loading $activeSection (${error::class.java.simpleName})")
                // Keep the durable account-scoped cache visible when the
                // request fails after process death or during an offline
                // transition. The error remains actionable via Retry.
                val latestCached = runCatching { cachedHouseholdListings() }.getOrDefault(cached)
                _state.value = _state.value.copy(
                    loading = false,
                    listings = latestCached.ifEmpty { _state.value.listings },
                    error = householdRefreshError(error, activeSection)
                )
            }
        }
    }
    fun searchHouseholdKabadiwalas(area: String, radiusKm: Int = _state.value.kabadiwalaRadiusKm, location: CurrentLocation? = null) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        val query = area.trim()
        if (location == null && query.isBlank()) {
            _state.value = _state.value.copy(kabadiwalaAreaQuery = "", kabadiwalaLatitude = null, kabadiwalaLongitude = null, kabadiwalas = emptyList(), kabadiwalaRequiresLocation = true, kabadiwalaHasMore = false)
            return
        }
        resetForAccountChange()
        _state.value = _state.value.copy(kabadiwalaAreaQuery = query, kabadiwalaLatitude = location?.latitude, kabadiwalaLongitude = location?.longitude, kabadiwalaRadiusKm = radiusKm, kabadiwalaLoading = true, error = null)
        viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwalas(location?.latitude, location?.longitude, radiusKm, query.ifBlank { null }, 1).requireData().toKabadiwalaDirectoryDto() }
                .onSuccess { directory ->
                    _state.value = _state.value.copy(kabadiwalas = directory.items, kabadiwalaPage = 1, kabadiwalaHasMore = directory.pagination.page < directory.pagination.totalPages, kabadiwalaRequiresLocation = directory.requiresLocation, kabadiwalaLoading = false, error = null)
                }
                .onFailure { error -> _state.value = _state.value.copy(kabadiwalaLoading = false, error = friendly(error)) }
        }
    }
    fun loadMoreHouseholdKabadiwalas() {
        val current = _state.value
        if (current.kabadiwalaLoading || !current.kabadiwalaHasMore) return
        _state.value = current.copy(kabadiwalaLoading = true)
        viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwalas(current.kabadiwalaLatitude, current.kabadiwalaLongitude, current.kabadiwalaRadiusKm, current.kabadiwalaAreaQuery.ifBlank { null }, current.kabadiwalaPage + 1).requireData().toKabadiwalaDirectoryDto() }
                .onSuccess { directory ->
                    _state.value = _state.value.copy(kabadiwalas = _state.value.kabadiwalas + directory.items, kabadiwalaPage = directory.pagination.page, kabadiwalaHasMore = directory.pagination.page < directory.pagination.totalPages, kabadiwalaLoading = false)
                }
                .onFailure { error -> _state.value = _state.value.copy(kabadiwalaLoading = false, error = friendly(error)) }
        }
    }
    fun openKabadiwalaProfile(kabadiwalaId: String) {
        if (!allowed(AccountRole.HOUSEHOLD) || !protectedSessionReady()) return
        _state.value = _state.value.copy(selectedKabadiwalaId = kabadiwalaId, kabadiwalaProfile = null, kabadiwalaProfileLoading = true, error = null)
        viewModelScope.launch {
            runCatching { api.getHouseholdKabadiwala(kabadiwalaId, _state.value.kabadiwalaLatitude, _state.value.kabadiwalaLongitude).requireData() }
                .onSuccess { profile -> _state.value = _state.value.copy(kabadiwalaProfile = profile, kabadiwalaProfileLoading = false) }
                .onFailure { error -> _state.value = _state.value.copy(kabadiwalaProfileLoading = false, error = friendly(error)) }
        }
    }
    fun closeKabadiwalaProfile() { _state.value = _state.value.copy(selectedKabadiwalaId = null, kabadiwalaProfile = null, kabadiwalaProfileLoading = false) }
    fun increaseHouseholdRadius() {
        val next = when (_state.value.kabadiwalaRadiusKm) { 5 -> 10; 10 -> 25; 25 -> 50; 50 -> 100; 100 -> 200; else -> 200 }
        if (next != _state.value.kabadiwalaRadiusKm) searchHouseholdKabadiwalas(_state.value.kabadiwalaAreaQuery, next, _state.value.kabadiwalaLatitude?.let { CurrentLocation(it, _state.value.kabadiwalaLongitude ?: return, _state.value.kabadiwalaAreaQuery) })
    }
    fun refreshKabadiwala() {
        if (!allowed(AccountRole.COLLECTOR) || !protectedSessionReady()) return
        resetForAccountChange()
        cache?.load(accountId())?.let(::applyCached)
        load {
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (_: Exception) { partialFailure = true; fallback }
        val previous = _state.value
        val listings = optional(previous.listings) { api.getKabadiwalaListings().requireData() }
        val pickups = optional(previous.pickups) { api.getKabadiwalaPickups().requireData() }
        val inventory = optional(previous.inventory) { api.getKabadiwalaInventory().requireData() }
        val inventoryMovements = optional(previous.inventoryMovements) { api.getInventoryMovements(limit = 100).requireData() }
        val requirements = optional(previous.requirements) { api.getProcurementRequirements().requireData() }
        val offers = optional(previous.offers) { api.getKabadiwalaBulkOffers().requireData() }
        val bulkLots = optional(previous.bulkLots) { api.getKabadiwalaBulkLots().requireData() }
        val opportunities = optional(previous.poolOpportunities) { api.getPoolOpportunities().requireData() }
        val suggestions = optional(previous.poolSuggestions) { api.getPoolSuggestions().requireData() }
        val demandIntelligence = optional(previous.demandIntelligence) { api.getDemandIntelligence().requireData() }
        val pools = optional(previous.pools) { api.getKabadiwalaPools().requireData() }
        val handovers = optional(previous.handovers) { api.getKabadiwalaHandovers().requireData() }
        val passport = optional(previous.passport) { api.getCollectorPassport().requireData() }
        val safety = optional(previous.safety) { api.getSafety().requireData() }
        _state.value = _state.value.copy(loading = false, listings = listings, pickups = pickups, inventory = inventory, inventoryMovements = inventoryMovements, bulkLots = bulkLots, offers = offers, requirements = requirements, poolOpportunities = opportunities, poolSuggestions = suggestions, demandIntelligence = demandIntelligence, pools = pools, handovers = handovers, passport = passport, safety = safety, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Some saved evidence is shown because the network is unavailable." else null)
        saveCache()
        }
    }
    fun refreshRecycler() {
        if (!allowed(AccountRole.RECYCLER) || !protectedSessionReady()) return
        resetForAccountChange()
        cache?.load(accountId())?.let(::applyCached)
        load {
        var partialFailure = false
        suspend fun <T> optional(fallback: T, block: suspend () -> T): T = try { block() } catch (_: Exception) { partialFailure = true; fallback }
        val previous = _state.value
        val lots = optional(previous.bulkLots) { api.getRecyclerBulkLots().requireData() }
        val offers = optional(previous.offers) { api.getRecyclerBulkOffers().requireData() }
        val requirements = optional(previous.requirements) { api.getRecyclerProcurementRequirements().requireData() }
        val pools = optional(previous.pools) { api.getRecyclerPools().requireData() }
        val handovers = optional(previous.handovers) { api.getSupplyHandovers().requireData() }
        _state.value = _state.value.copy(loading = false, bulkLots = lots, offers = offers, requirements = requirements, pools = pools, handovers = handovers, showingCachedEvidence = partialFailure, cachedAtEpochMs = if (partialFailure) previous.cachedAtEpochMs else System.currentTimeMillis(), error = if (partialFailure) "Some saved evidence is shown because the network is unavailable." else null)
        saveCache()
        }
    }
    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, notice = null)
            runCatching { block() }.onFailure { _state.value = _state.value.copy(loading = false, error = friendly(it)) }
        }
    }
    private fun action(key: String, requiredRole: AccountRole? = null, block: suspend () -> String = { "Done" }) {
        if (!protectedSessionReady() || (requiredRole != null && !allowed(requiredRole))) return
        resetForAccountChange()
        if (key in _state.value.busy) return
        _state.value = _state.value.copy(busy = _state.value.busy + key, error = null, notice = null)
        viewModelScope.launch {
            runCatching { block() }.onSuccess { _state.value = _state.value.copy(notice = it) }.onFailure { _state.value = _state.value.copy(error = friendly(it)) }
            _state.value = _state.value.copy(busy = _state.value.busy - key)
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
    fun createListing(input: HouseholdListingCreateDto, localPhotoPath: String? = null) = createListing(input, listOfNotNull(localPhotoPath))
    fun createListing(input: HouseholdListingCreateDto, localPhotoPaths: List<String>) = action("create-listing", AccountRole.HOUSEHOLD, {
        require(localPhotoPaths.any { it.isNotBlank() && File(it).isFile }) { "Add at least one photo before posting." }
        val operation = "listing-${input.materialCategory}-${input.areaName}-${input.estimatedWeight}"
        val operationKey = idempotencyKeys?.getOrCreate(operation)
        val created = try {
            api.createHouseholdListing(input.copy(photoReference = null), operationKey).requireData()
        } catch (error: Throwable) {
            val transient = error is IOException || ((error as? RemoteApiException)?.httpCode ?: 0) >= 500
            if (!transient || syncQueue == null) throw error
            val account = accountId()?.takeIf { it.isNotBlank() }
            val localListingId = "local-${operationKey ?: UUID.nameUUIDFromBytes(operation.toByteArray()).toString()}"
            if (account != null) {
                householdListingCache?.upsert(
                    HouseholdListingDto(
                        id = localListingId,
                        householdId = account,
                        materialCategory = input.materialCategory,
                        estimatedWeight = input.estimatedWeight,
                        condition = input.condition,
                        notes = input.notes,
                        areaName = input.areaName,
                        latitude = input.latitude,
                        longitude = input.longitude,
                        estimatedPriceMin = input.estimatedPriceMin,
                        estimatedPriceMax = input.estimatedPriceMax,
                        status = "PENDING_SYNC"
                    ).toCacheEntity(account, synced = false)
                )
            }
            val payload = JsonObject().apply {
                add("input", com.google.gson.JsonParser.parseString(Gson().toJson(input.copy(photoReference = null))))
                operationKey?.let { addProperty("idempotencyKey", it) }
                addProperty("idempotencyOperation", operation)
                addProperty("localListingId", localListingId)
                val paths = localPhotoPaths.filter { it.isNotBlank() }
                if (paths.isNotEmpty()) {
                    add("photoPaths", com.google.gson.JsonArray().also { array -> paths.forEach(array::add) })
                    addProperty("photoPath", paths.first())
                }
            }
            val queueAccount = account?.takeIf { it.isNotBlank() }
            val alreadyQueued = operationKey?.let { key ->
                queueAccount?.let { owner -> syncQueue.findUidByOperationAndIdempotencyKey("CREATE_HOUSEHOLD_LISTING", owner, key) }
            }
            if (alreadyQueued == null) {
                syncQueue.enqueue(SyncQueueItemEntity(
                    operation = "CREATE_HOUSEHOLD_LISTING",
                    payloadJson = Gson().toJson(payload),
                    createdAtEpochMs = System.currentTimeMillis(),
                    accountId = queueAccount
                ))
            }
            // Keep the key until the worker receives an applied response. A
            // repeated offline tap must replay the same server mutation, not
            // create a second listing with a fresh idempotency key.
            requestSync?.invoke()
            return@action "Listing saved offline and will post when connected."
        }
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
        val operation = "pickup-$listingId-${kabadiwalaId ?: "waiting"}"
        val key = idempotencyKeys?.getOrCreate(operation) ?: "pickup-$listingId-${kabadiwalaId ?: "waiting"}"
        try {
            api.requestHouseholdPickup(listingId, PickupRequestCreateDto(kabadiwalaId), key).requireData()
            idempotencyKeys?.clear(operation)
            refreshHousehold()
            if (kabadiwalaId == null) "Pickup saved. We’ll notify nearby Kabadiwalas." else "Pickup request sent."
        } catch (error: Throwable) {
            val transient = error is IOException || ((error as? RemoteApiException)?.httpCode ?: 0) >= 500
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
                syncQueue.enqueue(
                    SyncQueueItemEntity(
                        operation = "REQUEST_HOUSEHOLD_PICKUP",
                        payloadJson = Gson().toJson(payload),
                        createdAtEpochMs = System.currentTimeMillis(),
                        accountId = queueAccount
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
        _state.value = _state.value.copy(materialSuggestion = null, materialDetectionStatus = HouseholdMaterialDetectionStatus.IDLE, materialDetectionMessage = null)
    }
    fun suggestHouseholdMaterial(path: String) {
        if (!allowed(AccountRole.HOUSEHOLD) || path.isBlank()) return
        if (_state.value.materialDetectionStatus == HouseholdMaterialDetectionStatus.PROCESSING) return
        _state.value = _state.value.copy(materialSuggestion = null, materialDetectionStatus = HouseholdMaterialDetectionStatus.PROCESSING, materialDetectionMessage = null)
        viewModelScope.launch {
            try {
                val source = File(path)
                val prepared = ImagePipeline.prepareForUpload(source, source.parentFile ?: File(System.getProperty("java.io.tmpdir").orEmpty()))
                val suggestion = try {
                    val body = prepared.asRequestBody(prepared.imageMimeType().toMediaTypeOrNull())
                    val language = LocaleManager.toBackendName(languageProvider()).toRequestBody("text/plain".toMediaTypeOrNull())
                    api.suggestLotMaterial(MultipartBody.Part.createFormData("photo", prepared.name, body), language).requireData()
                } finally {
                    prepared.delete()
                }
                val confident = suggestion.confidence >= 0.5 && !suggestion.materialCategory.equals("OTHER", ignoreCase = true)
                _state.value = _state.value.copy(materialSuggestion = suggestion, materialDetectionStatus = if (confident) HouseholdMaterialDetectionStatus.SUCCESS else HouseholdMaterialDetectionStatus.LOW_CONFIDENCE)
            } catch (_: IllegalArgumentException) {
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE, materialDetectionMessage = "This image could not be processed.")
            } catch (error: RemoteApiException) {
                val serviceFailure = error.httpCode == null || error.httpCode >= 500 || error.code == "SERVICE_UNAVAILABLE" || error.code == "GEMINI_UNAVAILABLE"
                _state.value = _state.value.copy(materialDetectionStatus = when { error.httpCode == 422 || error.code == "VALIDATION_ERROR" -> HouseholdMaterialDetectionStatus.UNSUPPORTED_IMAGE; serviceFailure -> HouseholdMaterialDetectionStatus.SERVICE_ERROR; else -> HouseholdMaterialDetectionStatus.NETWORK_ERROR }, materialDetectionMessage = null)
            } catch (_: IOException) {
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.NETWORK_ERROR, materialDetectionMessage = null)
            } catch (_: Exception) {
                _state.value = _state.value.copy(materialDetectionStatus = HouseholdMaterialDetectionStatus.SERVICE_ERROR, materialDetectionMessage = null)
            }
        }
    }

    fun loadKabadiwalaListingPhotos(listingId: String, photoCount: Int) = action("photos-$listingId", AccountRole.COLLECTOR, {
        val count = photoCount.coerceIn(1, 6)
        val photos = mutableListOf<ByteArray>()
        var partialFailure = false
        var firstFailure: Throwable? = null
        for (index in 0 until count) {
            try {
                val response = if (index == 0) {
                    api.getKabadiwalaListingPhoto(listingId)
                } else {
                    api.getKabadiwalaListingPhotoAtIndex(listingId, index)
                }
                photos += response.requireBody().bytes()
            } catch (error: Throwable) {
                partialFailure = true
                firstFailure = firstFailure ?: error
                break
            }
        }
        if (photos.isEmpty()) throw (firstFailure ?: IllegalStateException("No listing photos available"))
        _state.value = _state.value.copy(
            listingPhotos = _state.value.listingPhotos + (listingId to photos),
            listingPhotoErrors = if (partialFailure) {
                _state.value.listingPhotoErrors + (listingId to "Some angles are unavailable right now. You can retry.")
            } else {
                _state.value.listingPhotoErrors - listingId
            }
        )
        if (partialFailure) "Loaded ${photos.size} of $count scrap photos. You can retry for the remaining angles." else "${photos.size} scrap photo${if (photos.size == 1) "" else "s"} loaded."
    })
    fun cancelListing(listingId: String, reason: String? = null) = action("cancel-listing-$listingId", AccountRole.HOUSEHOLD, { api.cancelHouseholdListing(listingId, CancellationRequestDto(reason)).requireSuccess(); refreshHousehold(); "Listing cancelled." })
    fun cancelPickup(pickupId: String, reason: String? = null) = action("cancel-pickup-$pickupId", AccountRole.HOUSEHOLD, { api.cancelHouseholdPickup(pickupId, CancellationRequestDto(reason)).requireSuccess(); refreshHousehold(); "Pickup cancelled." })
    fun reschedulePickup(pickupId: String, scheduledSlot: String) = action("reschedule-$pickupId", AccountRole.HOUSEHOLD, { api.rescheduleHouseholdPickup(pickupId, PickupRescheduleDto(scheduledSlot)).requireData(); refreshHousehold(); "Pickup rescheduled." })
    fun decideHouseholdSettlement(pickupId: String, decision: String, reasonCode: String? = null, notes: String? = null) = action("settlement-$pickupId", AccountRole.HOUSEHOLD, { api.decideHouseholdSettlement(pickupId, SettlementDecisionDto(decision, reasonCode, null, notes)).requireData(); refreshHousehold(); "Settlement decision recorded." })
    fun acceptListing(listingId: String) = action("accept-$listingId", AccountRole.COLLECTOR, {
        try {
            api.acceptHouseholdListing(listingId).requireSuccess()
            refreshKabadiwala()
            "Pickup accepted."
        } catch (error: Throwable) {
            if ((error as? RemoteApiException)?.let { it.code == "PICKUP_NOT_AVAILABLE" || it.message.contains("no longer available", ignoreCase = true) || it.message.contains("not available to accept", ignoreCase = true) } == true) {
                refreshKabadiwala()
                "Another Kabadiwala already took this pickup. The queue is refreshing."
            } else throw error
        }
    })
    fun rejectPickup(pickupId: String, reason: String? = null) = action("reject-$pickupId", AccountRole.COLLECTOR, { api.rejectKabadiwalaPickup(pickupId, BulkOfferDecisionDto(reason)).requireSuccess(); refreshKabadiwala(); "Pickup declined and returned to the network." })
    fun confirmAvailability(pickupId: String, slot: String? = null) = action("availability-$pickupId", AccountRole.COLLECTOR, { api.confirmPickupAvailability(pickupId, PickupAvailabilityDto(true, slot)).requireData(); refreshKabadiwala(); "Availability confirmed." })
    fun schedulePickup(pickupId: String, iso: String) = action("schedule-$pickupId", AccountRole.COLLECTOR, { api.schedulePickup(pickupId, PickupScheduleDto(iso)).requireSuccess(); refreshKabadiwala(); "Pickup scheduled." })
    fun pickupStatus(pickupId: String, status: String) = action("status-$pickupId", AccountRole.COLLECTOR, { api.updatePickupStatus(pickupId, PickupStatusDto(status)).requireSuccess(); refreshKabadiwala(); "Pickup updated." })
    fun cancelKabadiwalaPickup(pickupId: String, reason: String? = null) = action("cancel-collector-$pickupId", AccountRole.COLLECTOR, { api.cancelKabadiwalaPickup(pickupId, CancellationRequestDto(reason)).requireSuccess(); refreshKabadiwala(); "Pickup cancelled." })
    fun reassignPickup(pickupId: String, reason: String, noShow: Boolean = false) = action("reassign-$pickupId", AccountRole.COLLECTOR, { api.reassignPickup(pickupId, PickupReassignDto(reason, noShow)).requireData(); refreshKabadiwala(); "Pickup returned to the network for reassignment." })
    fun completePickup(pickupId: String, input: PickupCompletionDto) = action("complete-$pickupId", AccountRole.COLLECTOR, { api.completePickup(pickupId, input).requireData(); refreshKabadiwala(); "Purchase completed and inventory updated." })
    fun recordPickupSettlementPayment(pickupId: String, input: PickupSettlementPaymentRequestDto) = action("pickup-payment-$pickupId", AccountRole.COLLECTOR, {
        api.recordPickupSettlementPayment(pickupId, input, UUID.randomUUID().toString()).requireData()
        refreshKabadiwala()
        "Payment recorded for operator reconciliation."
    })
    fun rateHouseholdPickup(pickupId: String, rating: Int) = action("rate-pickup-$pickupId", AccountRole.HOUSEHOLD, {
        api.reviewHouseholdPickup(pickupId, HouseholdPickupReviewDto(rating)).requireData()
        refreshHousehold()
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
        refreshKabadiwala()
        "Bulk lot listed for verified recyclers."
    }
    fun cancelBulkLot(lotId: String) = action("cancel-bulk-$lotId", AccountRole.COLLECTOR, { api.cancelBulkLot(lotId).requireData(); refreshKabadiwala(); "Bulk lot cancelled and stock released." })
    fun acceptOffer(offerId: String) = action("offer-$offerId", AccountRole.COLLECTOR, { api.acceptBulkOffer(offerId).requireData(); refreshKabadiwala(); "Recycler offer accepted; stock remains reserved." })
    fun rejectOffer(offerId: String, reason: String) = action("reject-offer-$offerId", AccountRole.COLLECTOR, { api.rejectBulkOffer(offerId, BulkOfferDecisionDto(reason.ifBlank { null })).requireData(); refreshKabadiwala(); "Offer rejected." })
    fun counterOffer(offerId: String, rate: Double, notes: String?) = action("counter-offer-$offerId", AccountRole.COLLECTOR, { api.counterBulkOffer(offerId, BulkOfferCounterDto(rate, notes?.ifBlank { null })).requireData(); refreshKabadiwala(); "Counter-offer sent." })
    fun makeOffer(lotId: String, rate: Double) = action("offer-$lotId", AccountRole.RECYCLER, { api.makeBulkLotOffer(lotId, BulkOfferCreateDto(rate)).requireData(); refreshRecycler(); "Offer sent to the Kabadiwala." })
    fun withdrawOffer(offerId: String, reason: String?) = action("withdraw-offer-$offerId", AccountRole.RECYCLER, { api.withdrawRecyclerOffer(offerId, BulkOfferDecisionDto(reason?.ifBlank { null })).requireData(); refreshRecycler(); "Offer withdrawn." })
    fun updateRequirement(requirementId: String, input: ProcurementRequirementUpdateDto) = action("update-demand-$requirementId", AccountRole.RECYCLER, { api.updateProcurementRequirement(requirementId, input).requireData(); refreshRecycler(); "Procurement requirement updated." })
    /**
     * Receiving is intentionally not implemented through the legacy
     * /recycler/bulk-lots/:lotId/receive compatibility guard. The production
     * workflow is the signed formal handover scanner and confirmation form.
     */
    fun receiveLot(lotId: String) = action("receive-$lotId", AccountRole.RECYCLER, { "Scan the signed handover QR to confirm receipt." })
    fun createRequirement(input: ProcurementRequirementCreateDto) = action("create-demand", AccountRole.RECYCLER, { api.createProcurementRequirement(input).requireData(); refreshRecycler(); "Requirement published to Kabadiwalas." })
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
        api.decideSupplySettlement(handoverId, SettlementDecisionDto(decision, reasonCode, evidenceReference, notes)).requireData()
        refreshKabadiwala()
        "Settlement decision recorded."
    }
    fun createPool(requirementId: String, areaName: String) = action("pool-create-$requirementId", AccountRole.COLLECTOR) { val pool = api.createPool(PoolCreateRequestDto(requirementId, areaName)).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); refreshKabadiwala(); "Cooperative pool opened. Other Kabadiwalas can contribute reserved stock." }
    fun joinPool(poolId: String, quantityKg: Double, grade: String, expectedRatePerKg: Double?) = action("pool-join-$poolId", AccountRole.COLLECTOR) { api.joinPool(poolId, PoolJoinRequestDto(quantityKg, grade, expectedRatePerKg)).requireData(); refreshKabadiwala(); "Stock reserved in the cooperative pool." }
    fun leavePool(poolId: String) = action("pool-leave-$poolId", AccountRole.COLLECTOR) { api.leavePool(poolId).requireData(); refreshKabadiwala(); "Contribution released back to available stock." }
    fun lockPool(poolId: String) = action("pool-lock-$poolId", AccountRole.COLLECTOR) { val pool = api.lockPool(poolId).requireData(); _state.value = _state.value.copy(pools = listOf(pool) + _state.value.pools.filterNot { it.id == pool.id }); saveCache(); "Pool locked at threshold. Prepare the one-time handover QR." }
    fun preparePoolHandover(poolId: String) = action("handover-pool-$poolId", AccountRole.COLLECTOR) { val handover = api.preparePoolHandover(poolId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
    fun prepareBulkHandover(lotId: String) = action("handover-bulk-$lotId", AccountRole.COLLECTOR) { val handover = api.prepareBulkHandover(lotId, JsonObject()).requireData(); _state.value = _state.value.copy(handovers = listOf(handover) + _state.value.handovers.filterNot { it.id == handover.id }); saveCache(); "One-time handover QR prepared: ${handover.referenceId}." }
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

package co.quis.flutter_contacts

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class FlutterContactsPlugin : FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware, ActivityResultListener, RequestPermissionsResultListener {
    companion object {
        private var activity: Activity? = null
        private var context: Context? = null
        private var resolver: ContentResolver? = null
        private val permissionReadWriteCode: Int = 0
        private val permissionReadOnlyCode: Int = 1
        private var permissionResult: Result? = null
        private var viewResult: Result? = null
        private var editResult: Result? = null
        private var pickResult: Result? = null
        private var insertResult: Result? = null

        private const val ACCOUNT_PARAMETER = "account_map"
        private const val RAW_CONTACT_IDS_PARAMETER = "raw_contact_ids"
        private const val RAW_CONTACT_ID_PARAMETER = "raw_contact_id"
        private const val ID_PARAMETER = "id"
        private const val WITH_PROPERTIES_PARAMETER = "with_properties"
        private const val WITH_THUMBNAIL_PARAMETER = "with_thumbnail"
        private const val WITH_PHOTO_PARAMETER = "with_photo"
        private const val WITH_GROUPS_PARAMETER = "with_groups"
        private const val WITH_ACCOUNTS_PARAMETER = "with_accounts"
        private const val RETURN_UNIFIED_CONTACTS_PARAMETER = "return_unified_contacts"
        private const val INCLUDE_NON_VISIBLE_PARAMETER = "include_non_visible"
        private const val ID_IS_RAW_CONTACT_ID_PARAMETER = "id_is_raw_contact_od"
        private const val MIMETYPE_PARAMETER = "mimetype"
        private const val PROJECTION_PARAMETER = "projection"
        private const val ROW_CONTENT_MAP_PARAMETER = "row_content_map"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // --- FlutterPlugin implementation ---

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val channel = MethodChannel(flutterPluginBinding.binaryMessenger, "github.com/QuisApp/flutter_contacts")
        val eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "github.com/QuisApp/flutter_contacts/events")
        channel.setMethodCallHandler(FlutterContactsPlugin())
        eventChannel.setStreamHandler(FlutterContactsPlugin())
        context = flutterPluginBinding.applicationContext
        resolver = context!!.contentResolver
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        coroutineScope.cancel()
    }

    // --- ActivityAware implementation ---

    override fun onDetachedFromActivity() { activity = null }

    override fun onDetachedFromActivityForConfigChanges() { activity = null }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
        binding.addActivityResultListener(this)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
        binding.addActivityResultListener(this)
    }

    // --- ActivityResultListener implementation ---

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?
    ): Boolean {
        when (requestCode) {
            FlutterContacts.REQUEST_CODE_VIEW ->
                if (viewResult != null) {
                    viewResult!!.success(null)
                    viewResult = null
                }
            FlutterContacts.REQUEST_CODE_EDIT ->
                if (editResult != null) {
                    // Result is of the form:
                    // content://com.android.contacts/contacts/lookup/<hash>/<id>
                    val id = intent?.getData()?.getLastPathSegment()
                    editResult!!.success(id)
                    editResult = null
                }
            FlutterContacts.REQUEST_CODE_PICK ->
                if (pickResult != null) {
                    // Result is of the form:
                    // content://com.android.contacts/contacts/lookup/<hash>/<id>
                    val id = intent?.getData()?.getLastPathSegment()
                    pickResult!!.success(id)
                    pickResult = null
                }
            FlutterContacts.REQUEST_CODE_INSERT ->
                if (insertResult != null) {
                    // Result is of the form:
                    // content://com.android.contacts/raw_contacts/<raw_id>
                    // So we need to get the ID from the raw ID.
                    val rawId = intent?.getData()?.getLastPathSegment()
                    if (rawId != null) {
                        val contacts: List<Map<String, Any?>> =
                            FlutterContacts.select(
                                resolver!!,
                                rawId,
                                /*withProperties=*/false,
                                /*withThumbnail=*/false,
                                /*withPhoto=*/false,
                                /*withGroups=*/false,
                                /*withAccounts=*/false,
                                /*returnUnifiedContacts=*/true,
                                /*includeNonVisible=*/true,
                                /*idIsRawContactId=*/true
                            )
                        if (contacts.isNotEmpty()) {
                            insertResult!!.success(contacts[0]["id"])
                        } else {
                            insertResult!!.success(null)
                        }
                    } else {
                        insertResult!!.success(null)
                    }
                    insertResult = null
                }
        }
        return true
    }

    // --- RequestPermissionsResultListener implementation ---

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        when (requestCode) {
            permissionReadWriteCode -> {
                val granted = grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED
                if (permissionResult != null) {
                    coroutineScope.launch(Dispatchers.Main) {
                        permissionResult?.success(granted)
                        permissionResult = null
                    }
                }
                return true
            }
            permissionReadOnlyCode -> {
                val granted = grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (permissionResult != null) {
                    coroutineScope.launch(Dispatchers.Main) {
                        permissionResult?.success(granted)
                        permissionResult = null
                    }
                }
                return true
            }
        }
        return false // did not handle the result
    }

    // --- MethodCallHandler implementation ---

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            // Requests permission to read/write contacts.
            "requestPermission" ->
                coroutineScope.launch(Dispatchers.IO) {
                    if (context == null) {
                        coroutineScope.launch(Dispatchers.Main) { result.success(false); }
                    } else {
                        val readonly = call.arguments as Boolean
                        val readPermission = Manifest.permission.READ_CONTACTS
                        val writePermission = Manifest.permission.WRITE_CONTACTS
                        if (ContextCompat.checkSelfPermission(context!!, readPermission) == PackageManager.PERMISSION_GRANTED &&
                            (readonly || ContextCompat.checkSelfPermission(context!!, writePermission) == PackageManager.PERMISSION_GRANTED)
                        ) {
                            coroutineScope.launch(Dispatchers.Main) { result.success(true) }
                        } else if (activity != null) {
                            permissionResult = result
                            if (readonly) {
                                ActivityCompat.requestPermissions(activity!!, arrayOf(readPermission), permissionReadOnlyCode)
                            } else {
                                ActivityCompat.requestPermissions(activity!!, arrayOf(readPermission, writePermission), permissionReadWriteCode)
                            }
                        }
                    }
                }
            // Selects fields for request contact, or for all contacts.
            "select" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val args = call.arguments as List<Any>
                    val id = args[0] as String?
                    val withProperties = args[1] as Boolean
                    val withThumbnail = args[2] as Boolean
                    val withPhoto = args[3] as Boolean
                    val withGroups = args[4] as Boolean
                    val withAccounts = args[5] as Boolean
                    val returnUnifiedContacts = args[6] as Boolean
                    val includeNonVisible = args[7] as Boolean
                    // args[8] = includeNotesOnIos13AndAbove
//                    val accountMap: HashMap<String, Any>?
//                    @Suppress("UNCHECKED_CAST")
//                    if (args[9] is HashMap<*, *>) accountMap = args[9] as HashMap<String, Any>
                    val contacts: List<Map<String, Any?>> =
                        FlutterContacts.select(
                            resolver!!,
                            id,
                            withProperties,
                            // Sometimes thumbnail is available but photo is not, so we
                            // fetch thumbnails even if only the photo was requested.
                            withThumbnail || withPhoto,
                            withPhoto,
                            withGroups,
                            withAccounts,
                            returnUnifiedContacts,
                            includeNonVisible
                        )
                    coroutineScope.launch(Dispatchers.Main) { result.success(contacts) }
                }
            // Selects fields for request contact, or for all contacts.
            //
            "selectAdvanced" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val id = call.argument<String>(ID_PARAMETER)
                    val withProperties = call.argument<Boolean>(WITH_PROPERTIES_PARAMETER) ?: false
                    val withThumbnail = call.argument<Boolean>(WITH_THUMBNAIL_PARAMETER) ?: false
                    val withPhoto = call.argument<Boolean>(WITH_PHOTO_PARAMETER) ?: false
                    val withGroups = call.argument<Boolean>(WITH_GROUPS_PARAMETER) ?: false
                    val withAccounts = call.argument<Boolean>(WITH_ACCOUNTS_PARAMETER) ?: false
                    val returnUnifiedContacts = call.argument<Boolean>(RETURN_UNIFIED_CONTACTS_PARAMETER) ?: false
                    val includeNonVisible = call.argument<Boolean>(INCLUDE_NON_VISIBLE_PARAMETER) ?: false
                    val idIsRawContactId = call.argument<Boolean>(ID_IS_RAW_CONTACT_ID_PARAMETER) ?: false
                    val accountMap = call.argument<HashMap<String, Any>?>(ACCOUNT_PARAMETER)

                    val contacts: List<Map<String, Any?>> =
                        FlutterContacts.select(
                            resolver = resolver!!,
                            id = id,
                            withProperties = withProperties,
                            // Sometimes thumbnail is available but photo is not, so we
                            // fetch thumbnails even if only the photo was requested.
                            withThumbnail = withThumbnail || withPhoto,
                            withPhoto = withPhoto,
                            withGroups = withGroups,
                            withAccounts = withAccounts,
                            returnUnifiedContacts = returnUnifiedContacts,
                            includeNonVisible = includeNonVisible,
                            idIsRawContactId = idIsRawContactId,
                            accountMap = accountMap
                        )
                    coroutineScope.launch(Dispatchers.Main) { result.success(contacts) }
                }
            /**
             * Query the contents of custom data rows.
             * See also doc of [FlutterContacts.queryCustomDataRows].
              */
            "queryCustomDataRows" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val rawContactId = call.argument<String>(RAW_CONTACT_ID_PARAMETER)
                    val mimeType = call.argument<String>(MIMETYPE_PARAMETER)
                    val projection = call.argument<List<String>?>(PROJECTION_PARAMETER)

                    if (rawContactId != null && mimeType != null) {
                        val customDataRows: List<Map<String, Any?>> =
                            FlutterContacts.queryCustomDataRows(
                                resolver = resolver!!,
                                rawContactId = rawContactId,
                                mimeType = mimeType,
                                projection = projection,
                            )
                        coroutineScope.launch(Dispatchers.Main) { result.success(customDataRows) }
                    } else {
                        coroutineScope.launch(Dispatchers.Main) { result.error("", "One of the required parameters was null", "") }
                    }
                }
            /**
             * Query the contents of custom data rows.
             * See also doc of [FlutterContacts.insertCustomDataRow].
             */
            "insertCustomDataRow" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val rawContactId = call.argument<String>(RAW_CONTACT_ID_PARAMETER)
                    val mimeType = call.argument<String>(MIMETYPE_PARAMETER)
                    val rowContentMap = call.argument<HashMap<String, Any>?>(ROW_CONTENT_MAP_PARAMETER)


                    if (rawContactId != null && mimeType != null && rowContentMap != null) {
                        FlutterContacts.insertCustomDataRow(
                            resolver = resolver!!,
                            rawContactId = rawContactId,
                            mimeType = mimeType,
                            rowContentMap = rowContentMap,
                            )
                        coroutineScope.launch(Dispatchers.Main) { result.success(null) }
                    } else {
                        coroutineScope.launch(Dispatchers.Main) { result.error("", "One of the required parameters was null", "") }
                    }
                    
                }
            // Gets all "soft deleted" contacts for a given account
            "queryDeleted" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val accountMap = call.argument<HashMap<String, Any>?>(ACCOUNT_PARAMETER)
                    val contacts: List<Map<String, Any?>> =
                        FlutterContacts.queryDeleted(
                            resolver!!,
                            accountMap
                        )
                    coroutineScope.launch(Dispatchers.Main) { result.success(contacts) }
                }
            // Gets all contacts marked dirty for a given account
            "queryDirty" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val accountMap = call.argument<HashMap<String, Any>?>(ACCOUNT_PARAMETER)
                    val contacts: List<Map<String, Any?>> =
                        FlutterContacts.queryDirty(
                            resolver!!,
                            accountMap
                        )
                    coroutineScope.launch(Dispatchers.Main) { result.success(contacts) }
                }
            // Clears dirty bit for given raw_contact_id
            "clearDirty" ->
                coroutineScope.launch(Dispatchers.IO) { // runs in a background thread
                    val rawIds = call.argument<List<String>>(RAW_CONTACT_IDS_PARAMETER)
                    val changedLines: Int =
                        rawIds?.let {
                            FlutterContacts.clearDirty(
                                resolver!!,
                                it
                            )
                        } ?: -1
                    coroutineScope.launch(Dispatchers.Main) { result.success(changedLines) }
                }
            // Inserts a new contact and return it.
            "insert" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val contact = args[0] as Map<String, Any>
                    // includeNotesOnIos13AndAbove = args[1]
                    val callerIsSyncAdapter = args[2] as Boolean? ?: false
                    val insertedContact: Map<String, Any?>? =
                        FlutterContacts.insert(resolver!!, contact, callerIsSyncAdapter)
                    coroutineScope.launch(Dispatchers.Main) {
                        if (insertedContact != null) {
                            result.success(insertedContact)
                        } else {
                            result.error("", "failed to create contact", "")
                        }
                    }
                }
            // Updates an existing contact and returns it.
            "update" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val contact = args[0] as Map<String, Any>
                    val withGroups = args[1] as Boolean
                    val updatedContact: Map<String, Any?>? =
                        FlutterContacts.update(resolver!!, contact, withGroups)
                    coroutineScope.launch(Dispatchers.Main) {
                        if (updatedContact != null) {
                            result.success(updatedContact)
                        } else {
                            result.error("", "failed to update contact", "")
                        }
                    }
                }
            // Updates an existing raw contact and returns it.
            "updateRaw" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val contact = args[0] as Map<String, Any>
                    val withGroups = args[1] as Boolean
                    // includeNotesOnIos13AndAbove = args[2]
                    val callerIsSyncAdapter = args[3] as Boolean? ?: false
                    val updatedContact: Map<String, Any?>? =
                        FlutterContacts.updateRaw(resolver!!, contact, withGroups, callerIsSyncAdapter)
                    coroutineScope.launch(Dispatchers.Main) {
                        if (updatedContact != null) {
                            result.success(updatedContact)
                        } else {
                            result.error("", "failed to update contact", "")
                        }
                    }
                }
            // Deletes contacts with given IDs.
            "delete" ->
                coroutineScope.launch(Dispatchers.IO) {
                    FlutterContacts.delete(resolver!!, call.arguments as List<String>)
                    coroutineScope.launch(Dispatchers.Main) { result.success(null) }
                }
            // Deletes raw contacts with given raw IDs.
            "deleteRaw" ->
                coroutineScope.launch(Dispatchers.IO) {
                    FlutterContacts.deleteRaw(resolver!!, call.arguments as List<String>)
                    coroutineScope.launch(Dispatchers.Main) { result.success(null) }
                }
            // Fetches all groups.
            "getGroups" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val groups: List<Map<String, Any?>> =
                        FlutterContacts.getGroups(resolver!!)
                    coroutineScope.launch(Dispatchers.Main) { result.success(groups) }
                }
            // Insert a new group and returns it.
            "insertGroup" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val group = args[0] as Map<String, Any>
                    val insertedGroup: Map<String, Any?>? =
                        FlutterContacts.insertGroup(resolver!!, group)
                    coroutineScope.launch(Dispatchers.Main) {
                        result.success(insertedGroup)
                    }
                }
            // Updates a group and returns it.
            "updateGroup" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val group = args[0] as Map<String, Any>
                    val updatedGroup: Map<String, Any?>? =
                        FlutterContacts.updateGroup(resolver!!, group)
                    coroutineScope.launch(Dispatchers.Main) {
                        result.success(updatedGroup)
                    }
                }
            // Deletes a group.
            "deleteGroup" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val group = args[0] as Map<String, Any>
                    FlutterContacts.deleteGroup(resolver!!, group)
                    coroutineScope.launch(Dispatchers.Main) {
                        result.success(null)
                    }
                }
            // Opens external contact app to view existing contact.
            "openExternalView" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val id = args[0] as String
                    FlutterContacts.openExternalViewOrEdit(activity, context, id, false)
                    viewResult = result
                }
            // Opens external contact app to edit existing contact.
            "openExternalEdit" ->
                coroutineScope.launch(Dispatchers.IO) {
                    val args = call.arguments as List<Any>
                    val id = args[0] as String
                    FlutterContacts.openExternalViewOrEdit(activity, context, id, true)
                    editResult = result
                }
            // Opens external contact app to pick an existing contact.
            "openExternalPick" ->
                coroutineScope.launch(Dispatchers.IO) {
                    FlutterContacts.openExternalPickOrInsert(activity, context, false)
                    pickResult = result
                }
            // Opens external contact app to insert a new contact.
            "openExternalInsert" ->
                coroutineScope.launch(Dispatchers.IO) {
                    var args = call.arguments as List<Any>
                    val contact = args.getOrNull(0)?.let { it as? Map<String, Any?> } ?: run {
                        null
                    }
                    FlutterContacts.openExternalPickOrInsert(activity, context, true, contact)
                    insertResult = result
                }
            else -> result.notImplemented()
        }
    }

    // --- StreamHandler implementation ---

    var _eventObserver: ContactChangeObserver? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        if (events != null) {
            this._eventObserver = ContactChangeObserver(android.os.Handler(), events)
            resolver?.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, this._eventObserver!!)
        }
    }

    override fun onCancel(arguments: Any?) {
        if (this._eventObserver != null) {
            resolver?.unregisterContentObserver(this._eventObserver!!)
        }
        this._eventObserver = null
    }
}
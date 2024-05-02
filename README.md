# flutter_contacts

Flutter plugin to read, create, update, delete and observe native contacts on Android and iOS, with group support, vCard support, and contact permission handling.

For an example app using the added features of this fork see [flutter_contacts_and_accounts_demo](https://github.com/audriga/flutter_contacts_and_accounts_demo/)

We have not changed the existing examples ( [`example/`](https://github.com/audriga/flutter_contacts/blob/master/example) and [`example_full/`](https://github.com/audriga/flutter_contacts/blob/master/example_full)) much, so we could recommend to use or demo app instead for reference.

Our integration tests lie in [flutter_contacts_and_accounts_demo/integration_test](https://github.com/audriga/flutter_contacts_and_accounts_demo/tree/main/integration_test). We recommend taking a look at them, since this shows how you can expect different functions to behave.

## Changes in this Fork

We created and fixed some platform-specific functionality for Android specifically when working on raw contacts.

### Updated Documentation

The original library contained confusing Documentation when it came to the concept of raw contacts and ids.
Specifically it referred to "raw account ids, even though accounts don't have ids in the Android Contact tables, and there is no such thing as a raw account.

We fixed this documentation to more accurately reflect the contents of https://developer.android.com/guide/topics/providers/contacts-provider#RawContactBasics

In general we took care to add accurate Documentation to all functions so we encourage to read those for the functions you plan to use.

### New CRUD Functions

* `updateRawContact`: Updates a raw contact by deleting that raw contacts properties and re-adding them
  - Intended to replace `updateContact` as that would  **delete all all raw contacts** associated with that unified contact (same contact_id, but arbitrary raw_contact_id)  but **only re-insert the first raw contact** (with the new properties)
  - This could potentially also lead to the deletion of that contact on a remote service.
  - So we strongly advice to always use `updateRawContact` and never use `updateContact` on Android.
* `deleteRawContacts` deletes raw contacts based on a list of `raw_cotnact_ids`
* `insertContact` actually already inserted raw contacts (and returned the corresponding unified contact). Updated checks and description

### Custom Properties

* General Properties every contact has:
  * Read/ write for `source_id` (String that uniquely identifies this row to its source account.)
  * Read `lastUpdatedTimestamp` (due to Android specific restrictions this can likely not be retrieved for deleted contacts)

#### Custom MIME-types

In Android, for a given contact in the `contacts` table there will be one or multiple raw contacts in the `raw_contacts` table.
And for every raw contact in the raw contacts table there will be several properties in the `data` table.

Every row is one property of a raw contact. The mimetype row determines what kind of property it is (Phone Number, Email Address) and 15 data rows hold the actual data.

We have added `queryCustomDataRows`, `insertCustomDataRow`, `updateCustomDataRows` and `deleteCustomDataRows` functions to manually interact with such data rows.

This can be useful for custom mime-types. Data rows with custom mime-types is how messenger apps such as WhatsApp, Telegram or Signal add the "Message +12345", "Call +123345", etc buttons to your contacts.

### New Querying Functions

- getRawContacts
  -  Convenience method that returns raw contacts (ignores value of `config.returnUnifiedContacts`)
  - Can set account property to restrict results to just raw contacts of that account
- `getRawContactByRawId` gets a single raw contact
- `getDeleted` gets "soft deleted" contacts
- `getDirty` gets raw contacts marked as dirty

### Sync adapter Functionality

Added `config.behaveAsSyncAdapter` option: On Android, whenever you modify a raw contact (note that a unified contact is just a "merged" representation of one or multiple raw contacts, a unified contact does not have properties itself) a `dirty` flag is set, and when you delete a raw contact, the properties in the data table are deleted, but the raw contact in the raw_contacts table remains, and receives the `deleted` flag.

This is so that a sync adapter can then upload these changes to whatever online sync service it provides.
If you write a sync adapter want to avoid setting those flags (how else could you differentiate between changes you made at sync at changes the user made)

If `config.behaveAsSyncAdapter` is set to true, and you use the rawContact-CRUD methods laid out in "New Functions", these flags will not be set.

Also we provide

* `clearDirtyContacts` which clears the `dirty` flags for all given contacts

### Warnings

These are things we haven't fixed (yet)

* Do not use `updateContact` (see "New Functions" above)
* `contact.id` will sometimes correspond to `contact_id` and sometimes to `raw_contact_id` depending on the value of `config.returnUnifiedContacts`.
* `insertContact` will always return the corresponding unified contact, ignoring the value of `config.returnUnifiedContact`
* The Object model should does not reflect how the different contact tables relate on android. However we wont change this until we verified the inner workings of the iOS side of the plugin.
  * A unified contact should have a list of contributing raw contacts
  * A raw contact should have both a `contact_id` and  a `raw_contact_id`, furthermore it should have the mime-types list and exactly one account
  * An account has only a name and a type (no mime-types and no ids!)

## Installation

1. Add the following key/value pair to your app's `Info.plist` (for iOS):
    ```xml
    <plist version="1.0">
    <dict>
        ...
        <key>NSContactsUsageDescription</key>
        <string>Reason we need access to the contact list</string>
    </dict>
    </plist>
    ```
1. Add the following `<uses-permissions>` tags to your app's `AndroidManifest.xml` (for
   Android):
    ```xml
    <manifest xmlns:android="http://schemas.android.com/apk/res/android" ...>
        <uses-permission android:name="android.permission.READ_CONTACTS"/>
        <uses-permission android:name="android.permission.WRITE_CONTACTS"/>
        <application ...>
        ...
    ```

## Notes

* On iOS13+ you can only access notes if your app is
  [entitled by Apple](https://developer.apple.com/documentation/bundleresources/entitlements/com_apple_developer_contacts_notes)
  so notes are disabled by default. If you get entitlement, enable them via
  ```dart
  FlutterContacts.config.includeNotesOnIos13AndAbove = true;
  ```
* On both iOS and Android there is a concept of **raw** and **unified** contacts. A
  single person might have two raw contacts (for example from Gmail and from iCloud) but
  will be merged into a single view called a unified contact. In a contact app you
  typically want unified contacts, so this is what's returned by default. You can get
  raw contacts instead via
  ```dart
  FlutterContacts.config.returnUnifiedContacts = false;
  ```
  

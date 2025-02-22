/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.metrics

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.service.glean.BuildConfig
import mozilla.components.service.glean.Glean
import mozilla.components.service.glean.config.Configuration
import mozilla.components.service.glean.private.NoExtraKeys
import mozilla.components.support.utils.Browsers
import org.mozilla.fenix.GleanMetrics.BookmarksManagement
import org.mozilla.fenix.GleanMetrics.Collections
import org.mozilla.fenix.GleanMetrics.ContextMenu
import org.mozilla.fenix.GleanMetrics.CrashReporter
import org.mozilla.fenix.GleanMetrics.CustomTab
import org.mozilla.fenix.GleanMetrics.ErrorPage
import org.mozilla.fenix.GleanMetrics.Events
import org.mozilla.fenix.GleanMetrics.FindInPage
import org.mozilla.fenix.GleanMetrics.History
import org.mozilla.fenix.GleanMetrics.Library
import org.mozilla.fenix.GleanMetrics.MediaNotification
import org.mozilla.fenix.GleanMetrics.Metrics
import org.mozilla.fenix.GleanMetrics.Pings
import org.mozilla.fenix.GleanMetrics.PrivateBrowsingMode
import org.mozilla.fenix.GleanMetrics.PrivateBrowsingShortcut
import org.mozilla.fenix.GleanMetrics.QrScanner
import org.mozilla.fenix.GleanMetrics.QuickActionSheet
import org.mozilla.fenix.GleanMetrics.ReaderMode
import org.mozilla.fenix.GleanMetrics.SearchDefaultEngine
import org.mozilla.fenix.GleanMetrics.SearchShortcuts
import org.mozilla.fenix.GleanMetrics.SearchWidget
import org.mozilla.fenix.GleanMetrics.SyncAccount
import org.mozilla.fenix.GleanMetrics.SyncAuth
import org.mozilla.fenix.GleanMetrics.Tab
import org.mozilla.fenix.GleanMetrics.TrackingProtection
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.settings

private class EventWrapper<T : Enum<T>>(
    private val recorder: ((Map<T, String>?) -> Unit),
    private val keyMapper: ((String) -> T)? = null
) {
    private val String.asCamelCase: String
        get() = this.split("_").reduceIndexed { index, acc, s ->
            if (index == 0) acc + s
            else acc + s.capitalize()
        }

    fun track(event: Event) {
        val extras = if (keyMapper != null) {
            event.extras?.mapKeys { keyMapper.invoke(it.key.toString().asCamelCase) }
        } else {
            null
        }

        this.recorder(extras)
    }
}

private val Event.wrapper: EventWrapper<*>?
    get() = when (this) {
        is Event.OpenedApp -> EventWrapper(
            { Events.appOpened.record(it) },
            { Events.appOpenedKeys.valueOf(it) }
        )
        is Event.SearchBarTapped -> EventWrapper(
            { Events.searchBarTapped.record(it) },
            { Events.searchBarTappedKeys.valueOf(it) }
        )
        is Event.EnteredUrl -> EventWrapper(
            { Events.enteredUrl.record(it) },
            { Events.enteredUrlKeys.valueOf(it) }
        )
        is Event.PerformedSearch -> EventWrapper(
            {
                Metrics.searchCount[this.eventSource.countLabel].add(1)
                Events.performedSearch.record(it)
            },
            { Events.performedSearchKeys.valueOf(it) }
        )
        is Event.SearchShortcutMenuOpened -> EventWrapper<NoExtraKeys>(
            { SearchShortcuts.opened.record(it) }
        )
        is Event.SearchShortcutMenuClosed -> EventWrapper<NoExtraKeys>(
            { SearchShortcuts.closed.record(it) }
        )
        is Event.SearchShortcutSelected -> EventWrapper(
            { SearchShortcuts.selected.record(it) },
            { SearchShortcuts.selectedKeys.valueOf(it) }
        )
        is Event.ReaderModeAvailable -> EventWrapper<NoExtraKeys>(
            { ReaderMode.available.record(it) }
        )
        is Event.FindInPageOpened -> EventWrapper<NoExtraKeys>(
            { FindInPage.opened.record(it) }
        )
        is Event.FindInPageClosed -> EventWrapper<NoExtraKeys>(
            { FindInPage.closed.record(it) }
        )
        is Event.FindInPageNext -> EventWrapper<NoExtraKeys>(
            { FindInPage.nextResult.record(it) }
        )
        is Event.FindInPagePrevious -> EventWrapper<NoExtraKeys>(
            { FindInPage.previousResult.record(it) }
        )
        is Event.FindInPageSearchCommitted -> EventWrapper<NoExtraKeys>(
            { FindInPage.searchedPage.record(it) }
        )
        is Event.ContextMenuItemTapped -> EventWrapper(
            { ContextMenu.itemTapped.record(it) },
            { ContextMenu.itemTappedKeys.valueOf(it) }
        )
        is Event.CrashReporterOpened -> EventWrapper<NoExtraKeys>(
            { CrashReporter.opened.record(it) }
        )
        is Event.CrashReporterClosed -> EventWrapper(
            { CrashReporter.closed.record(it) },
            { CrashReporter.closedKeys.valueOf(it) }
        )
        is Event.BrowserMenuItemTapped -> EventWrapper(
            { Events.browserMenuAction.record(it) },
            { Events.browserMenuActionKeys.valueOf(it) }
        )
        is Event.QuickActionSheetOpened -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.opened.record(it) }
        )
        is Event.QuickActionSheetClosed -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.closed.record(it) }
        )
        is Event.QuickActionSheetShareTapped -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.shareTapped.record(it) }
        )
        is Event.QuickActionSheetBookmarkTapped -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.bookmarkTapped.record(it) }
        )
        is Event.QuickActionSheetDownloadTapped -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.downloadTapped.record(it) }
        )
        is Event.QuickActionSheetOpenInAppTapped -> EventWrapper<NoExtraKeys>(
            { QuickActionSheet.openAppTapped.record(it) }
        )
        is Event.OpenedBookmarkInNewTab -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.openInNewTab.record(it) }
        )
        is Event.OpenedBookmarksInNewTabs -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.openInNewTabs.record(it) }
        )
        is Event.OpenedBookmarkInPrivateTab -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.openInPrivateTab.record(it) }
        )
        is Event.OpenedBookmarksInPrivateTabs -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.openInPrivateTabs.record(it) }
        )
        is Event.EditedBookmark -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.edited.record(it) }
        )
        is Event.MovedBookmark -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.moved.record(it) }
        )
        is Event.RemoveBookmark -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.removed.record(it) }
        )
        is Event.RemoveBookmarks -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.multiRemoved.record(it) }
        )
        is Event.ShareBookmark -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.shared.record(it) }
        )
        is Event.CopyBookmark -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.copied.record(it) }
        )
        is Event.AddBookmarkFolder -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.folderAdd.record(it) }
        )
        is Event.RemoveBookmarkFolder -> EventWrapper<NoExtraKeys>(
            { BookmarksManagement.folderRemove.record(it) }
        )
        is Event.CustomTabsMenuOpened -> EventWrapper<NoExtraKeys>(
            { CustomTab.menu.record(it) }
        )
        is Event.CustomTabsActionTapped -> EventWrapper<NoExtraKeys>(
            { CustomTab.actionButton.record(it) }
        )
        is Event.CustomTabsClosed -> EventWrapper<NoExtraKeys>(
            { CustomTab.closed.record(it) }
        )
        is Event.UriOpened -> EventWrapper<NoExtraKeys>(
            { Events.totalUriCount.add(1) }
        )
        is Event.QRScannerOpened -> EventWrapper<NoExtraKeys>(
            { QrScanner.opened.record(it) }
        )
        is Event.QRScannerPromptDisplayed -> EventWrapper<NoExtraKeys>(
            { QrScanner.promptDisplayed.record(it) }
        )
        is Event.QRScannerNavigationAllowed -> EventWrapper<NoExtraKeys>(
            { QrScanner.navigationAllowed.record(it) }
        )
        is Event.QRScannerNavigationDenied -> EventWrapper<NoExtraKeys>(
            { QrScanner.navigationDenied.record(it) }
        )
        is Event.LibraryOpened -> EventWrapper<NoExtraKeys>(
            { Library.opened.record(it) }
        )
        is Event.LibraryClosed -> EventWrapper<NoExtraKeys>(
            { Library.closed.record(it) }
        )
        is Event.LibrarySelectedItem -> EventWrapper(
            { Library.selectedItem.record(it) },
            { Library.selectedItemKeys.valueOf(it) }
        )
        is Event.ErrorPageVisited -> EventWrapper(
            { ErrorPage.visitedError.record(it) },
            { ErrorPage.visitedErrorKeys.valueOf(it) }
        )
        is Event.SyncAuthOpened -> EventWrapper<NoExtraKeys>(
            { SyncAuth.opened.record(it) }
        )
        is Event.SyncAuthClosed -> EventWrapper<NoExtraKeys>(
            { SyncAuth.closed.record(it) }
        )
        is Event.SyncAuthSignIn -> EventWrapper<NoExtraKeys>(
            { SyncAuth.signIn.record(it) }
        )
        is Event.SyncAuthSignUp -> EventWrapper<NoExtraKeys>(
            { SyncAuth.signUp.record(it) }
        )
        is Event.SyncAuthPaired -> EventWrapper<NoExtraKeys>(
            { SyncAuth.paired.record(it) }
        )
        is Event.SyncAuthOtherExternal -> EventWrapper<NoExtraKeys>(
            { SyncAuth.otherExternal.record(it) }
        )
        is Event.SyncAuthFromShared -> EventWrapper<NoExtraKeys>(
            { SyncAuth.autoLogin.record(it) }
        )
        is Event.SyncAuthRecovered -> EventWrapper<NoExtraKeys>(
            { SyncAuth.recovered.record(it) }
        )
        is Event.SyncAuthSignOut -> EventWrapper<NoExtraKeys>(
            { SyncAuth.signOut.record(it) }
        )
        is Event.SyncAuthScanPairing -> EventWrapper<NoExtraKeys>(
            { SyncAuth.scanPairing.record(it) }
        )
        is Event.SyncAccountOpened -> EventWrapper<NoExtraKeys>(
            { SyncAccount.opened.record(it) }
        )
        is Event.SyncAccountClosed -> EventWrapper<NoExtraKeys>(
            { SyncAccount.closed.record(it) }
        )
        is Event.SyncAccountSyncNow -> EventWrapper<NoExtraKeys>(
            { SyncAccount.syncNow.record(it) }
        )
        is Event.SignInToSendTab -> EventWrapper<NoExtraKeys>(
            { SyncAccount.signInToSendTab.record(it) }
        )
        is Event.SendTab -> EventWrapper<NoExtraKeys>(
            { SyncAccount.sendTab.record(it) }
        )
        is Event.PreferenceToggled -> EventWrapper(
            { Events.preferenceToggled.record(it) },
            { Events.preferenceToggledKeys.valueOf(it) }
        )
        is Event.HistoryOpened -> EventWrapper<NoExtraKeys>(
            { History.opened.record(it) }
        )
        is Event.HistoryItemShared -> EventWrapper<NoExtraKeys>(
            { History.shared.record(it) }
        )
        is Event.HistoryItemOpened -> EventWrapper<NoExtraKeys>(
            { History.openedItem.record(it) }
        )
        is Event.HistoryItemRemoved -> EventWrapper<NoExtraKeys>(
            { History.removed.record(it) }
        )
        is Event.HistoryAllItemsRemoved -> EventWrapper<NoExtraKeys>(
            { History.removedAll.record(it) }
        )
        is Event.CollectionRenamed -> EventWrapper<NoExtraKeys>(
            { Collections.renamed.record(it) }
        )
        is Event.CollectionTabRestored -> EventWrapper<NoExtraKeys>(
            { Collections.tabRestored.record(it) }
        )
        is Event.CollectionAllTabsRestored -> EventWrapper<NoExtraKeys>(
            { Collections.allTabsRestored.record(it) }
        )
        is Event.CollectionTabRemoved -> EventWrapper<NoExtraKeys>(
            { Collections.tabRemoved.record(it) }
        )
        is Event.CollectionShared -> EventWrapper<NoExtraKeys>(
            { Collections.shared.record(it) }
        )
        is Event.CollectionRemoved -> EventWrapper<NoExtraKeys>(
            { Collections.removed.record(it) }
        )
        is Event.CollectionTabSelectOpened -> EventWrapper<NoExtraKeys>(
            { Collections.tabSelectOpened.record(it) }
        )
        is Event.ReaderModeOpened -> EventWrapper<NoExtraKeys>(
            { ReaderMode.opened.record(it) }
        )
        is Event.ReaderModeAppearanceOpened -> EventWrapper<NoExtraKeys>(
            { ReaderMode.appearance.record(it) }
        )
        is Event.CollectionTabLongPressed -> EventWrapper<NoExtraKeys>(
            { Collections.longPress.record(it) }
        )
        is Event.CollectionSaveButtonPressed -> EventWrapper(
            { Collections.saveButton.record(it) },
            { Collections.saveButtonKeys.valueOf(it) }
        )
        is Event.CollectionAddTabPressed -> EventWrapper<NoExtraKeys>(
            { Collections.addTabButton.record(it) }
        )
        is Event.CollectionRenamePressed -> EventWrapper<NoExtraKeys>(
            { Collections.renameButton.record(it) }
        )
        is Event.CollectionSaved -> EventWrapper(
            { Collections.saved.record(it) },
            { Collections.savedKeys.valueOf(it) }
        )
        is Event.CollectionTabsAdded -> EventWrapper(
            { Collections.tabsAdded.record(it) },
            { Collections.tabsAddedKeys.valueOf(it) }
        )
        is Event.SearchWidgetNewTabPressed -> EventWrapper<NoExtraKeys>(
            { SearchWidget.newTabButton.record(it) }
        )
        is Event.SearchWidgetVoiceSearchPressed -> EventWrapper<NoExtraKeys>(
            { SearchWidget.voiceButton.record(it) }
        )
        is Event.PrivateBrowsingGarbageIconTapped -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingMode.garbageIcon.record(it) }
        )
        is Event.PrivateBrowsingSnackbarUndoTapped -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingMode.snackbarUndo.record(it) }
        )
        is Event.PrivateBrowsingNotificationTapped -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingMode.notificationTapped.record(it) }
        )
        is Event.PrivateBrowsingNotificationOpenTapped -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingMode.notificationOpen.record(it) }
        )
        is Event.PrivateBrowsingNotificationDeleteAndOpenTapped -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingMode.notificationDelete.record(it) }
        )
        is Event.PrivateBrowsingCreateShortcut -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.createShortcut.record(it) }
        )
        is Event.PrivateBrowsingAddShortcutCFR -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.cfrAddShortcut.record(it) }
        )
        is Event.PrivateBrowsingCancelCFR -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.cfrCancel.record(it) }
        )
        is Event.PrivateBrowsingPinnedShortcutPrivateTab -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.pinnedShortcutPriv.record(it) }
        )
        is Event.PrivateBrowsingStaticShortcutTab -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.staticShortcutTab.record(it) }
        )
        is Event.PrivateBrowsingStaticShortcutPrivateTab -> EventWrapper<NoExtraKeys>(
            { PrivateBrowsingShortcut.staticShortcutPriv.record(it) }
        )
        is Event.WhatsNewTapped -> EventWrapper(
            { Events.whatsNewTapped.record(it) },
            { Events.whatsNewTappedKeys.valueOf(it) }
        )
        is Event.TabMediaPlay -> EventWrapper<NoExtraKeys>(
            { Tab.mediaPlay.record(it) }
        )
        is Event.TabMediaPause -> EventWrapper<NoExtraKeys>(
            { Tab.mediaPause.record(it) }
        )
        is Event.NotificationMediaPlay -> EventWrapper<NoExtraKeys>(
            { MediaNotification.play.record(it) }
        )
        is Event.NotificationMediaPause -> EventWrapper<NoExtraKeys>(
            { MediaNotification.pause.record(it) }
        )
        is Event.TrackingProtectionTrackerList -> EventWrapper<NoExtraKeys>(
            { TrackingProtection.etpTrackerList.record(it) }
        )
        is Event.TrackingProtectionIconPressed -> EventWrapper<NoExtraKeys>(
            { TrackingProtection.etpShield.record(it) }
        )
        is Event.TrackingProtectionSettingsPanel -> EventWrapper<NoExtraKeys>(
            { TrackingProtection.panelSettings.record(it) }
        )
        is Event.TrackingProtectionSettings -> EventWrapper<NoExtraKeys>(
            { TrackingProtection.etpSettings.record(it) }
        )
        is Event.TrackingProtectionException -> EventWrapper<NoExtraKeys>(
            { TrackingProtection.exceptionAdded.record(it) }
        )
        is Event.TrackingProtectionSettingChanged -> EventWrapper(
            { TrackingProtection.etpSettingChanged.record(it) },
            { TrackingProtection.etpSettingChangedKeys.valueOf(it) }
        )
        // Don't record other events in Glean:
        is Event.AddBookmark -> null
        is Event.OpenedBookmark -> null
        is Event.OpenedAppFirstRun -> null
        is Event.InteractWithSearchURLArea -> null
        is Event.ClearedPrivateData -> null
        is Event.DismissedOnboarding -> null
    }

class GleanMetricsService(private val context: Context) : MetricsService {
    private var initialized = false
    /*
     * We need to keep an eye on when we are done starting so that we don't
     * accidentally stop ourselves before we've ever started.
     */
    private lateinit var starter: Job

    private val activationPing = ActivationPing(context)

    override fun start() {
        Glean.setUploadEnabled(true)

        if (initialized) return
        initialized = true

        // We have to initialize Glean *on* the main thread, because it registers lifecycle
        // observers. However, the activation ping must be sent *off* of the main thread,
        // because it calls Google ad APIs that must be called *off* of the main thread.
        // These two things actually happen in parallel, but that should be ok because Glean
        // can handle events being recorded before it's initialized.
        starter = MainScope().launch {
            Glean.registerPings(Pings)
            Glean.initialize(context, Configuration(channel = BuildConfig.BUILD_TYPE))
        }

        setStartupMetrics()
    }

    internal fun setStartupMetrics() {
        Metrics.apply {
            defaultBrowser.set(Browsers.all(context).isDefaultBrowser)
            MozillaProductDetector.getMozillaBrowserDefault(context)?.also {
                defaultMozBrowser.set(it)
            }
            mozillaProducts.set(MozillaProductDetector.getInstalledMozillaProducts(context))
            adjustCampaign.set(context.settings().adjustCampaignId)
        }

        SearchDefaultEngine.apply {
            val defaultEngine = context
                .components
                .search
                .searchEngineManager
                .defaultSearchEngine ?: return@apply

            code.set(defaultEngine.identifier)
            name.set(defaultEngine.name)
            submissionUrl.set(defaultEngine.buildSearchUrl(""))
        }

        activationPing.checkAndSend()
    }

    override fun stop() {
        /*
         * We cannot stop until we're done starting.
         */
        runBlocking { starter.join(); }
        Glean.setUploadEnabled(false)
    }

    override fun track(event: Event) {
        event.wrapper?.track(event)
    }

    override fun shouldTrack(event: Event): Boolean {
        return event.wrapper != null
    }
}

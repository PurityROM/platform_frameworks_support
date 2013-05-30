/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v7.app;

import android.content.Context;
import android.support.v4.view.ActionProvider;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouteSelector;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/**
 * The media route action provider displays a {@link MediaRouteButton media route button}
 * in the application's {@link ActionBar} to allow the user to select routes and
 * to control the currently selected route.
 * <p>
 * The application must specify the kinds of routes that the user should be allowed
 * to select by specifying a {@link MediaRouteSelector selector} with the
 * {@link #setRouteSelector} method.
 * </p><p>
 * Refer to {@link MediaRouteButton} for a description of the button that will
 * appear in the action bar menu.  Note that instead of disabling the button
 * when no routes are available, the action provider will instead make the
 * menu item invisible.  In this way, the button will only be visible when it
 * is possible for the user to discover and select a matching route.
 * </p>
 *
 * <h3>Prerequisites</h3>
 * <p>
 * To use the media route action provider, the activity must be a subclass of
 * {@link ActionBarActivity} from the <code>android.support.v7.appcompat</code>
 * support library.  Refer to support library documentation for details.
 * </p>
 *
 * <h3>Example</h3>
 * <p>
 * </p><p>
 * The application should define a menu resource to include the provider in the
 * action bar options menu.  Note that the support library action bar uses attributes
 * that are defined in the application's resource namespace rather than the framework's
 * resource namespace to configure each item.
 * </p><pre>
 * &lt;menu xmlns:android="http://schemas.android.com/apk/res/android"
 *         xmlns:app="http://schemas.android.com/apk/res-auto">
 *     &lt;item android:id="@+id/media_route_menu_item"
 *         android:title="@string/media_route_menu_title"
 *         app:showAsAction="always"
 *         app:actionProviderClass="android.support.v7.app.MediaRouteActionProvider"/>
 * &lt;/menu>
 * </pre><p>
 * Then configure the menu and set the route selector for the chooser.
 * </p><pre>
 * public class MyActivity extends ActionBarActivity {
 *     private MediaRouter mRouter;
 *     private MediaRouter.Callback mCallback;
 *     private MediaRouteSelector mSelector;
 *
 *     protected void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *
 *         mRouter = Mediarouter.getInstance(this);
 *         mSelector = new MediaRouteSelector.Builder()
 *                 .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
 *                 .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
 *                 .build();
 *         mCallback = new MyCallback();
 *     }
 *
 *     // Add the callback on resume to tell the media router what kinds of routes
 *     // the application is interested in so that it can try to discover suitable ones.
 *     public void onResume() {
 *         super.onResume();
 *
 *         mediaRouter.addCallback(mSelector, mCallback);
 *
 *         MediaRouter.RouteInfo route = mediaRouter.updateSelectedRoute(mSelector);
 *         // do something with the route...
 *     }
 *
 *     // Remove the selector on pause to tell the media router that it no longer
 *     // needs to invest effort trying to discover routes of these kinds for now.
 *     public void onPause() {
 *         super.onPause();
 *
 *         mediaRouter.removeCallback(mCallback);
 *     }
 *
 *     public boolean onCreateOptionsMenu(Menu menu) {
 *         super.onCreateOptionsMenu(menu);
 *
 *         getMenuInflater().inflate(R.menu.sample_media_router_menu, menu);
 *
 *         MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
 *         MediaRouteActionProvider mediaRouteActionProvider =
 *                 (MediaRouteActionProvider)MenuItemCompat.getActionProvider(mediaRouteMenuItem);
 *         mediaRouteActionProvider.setRouteSelector(mSelector);
 *         return true;
 *     }
 *
 *     private final class MyCallback extends MediaRouter.Callback {
 *         // Implement callback methods as needed.
 *     }
 * }
 * </pre>
 *
 * @see #setRouteSelector
 */
public class MediaRouteActionProvider extends ActionProvider {
    private static final String TAG = "MediaRouteActionProvider";

    private final MediaRouter mRouter;
    private final MediaRouterCallback mCallback;

    private MediaRouteSelector mSelector = MediaRouteSelector.EMPTY;
    private MediaRouteButton mButton;

    /**
     * Creates the action provider.
     *
     * @param context The context.
     */
    public MediaRouteActionProvider(Context context) {
        super(context);

        mRouter = MediaRouter.getInstance(context);
        mCallback = new MediaRouterCallback(this);
    }

    /**
     * Gets the media route selector for filtering the routes that the user can
     * select using the media route chooser dialog.
     *
     * @return The selector, never null.
     */
    public MediaRouteSelector getRouteSelector() {
        return mSelector;
    }

    /**
     * Sets the media route selector for filtering the routes that the user can
     * select using the media route chooser dialog.
     *
     * @param selector The selector, must not be null.
     */
    public void setRouteSelector(MediaRouteSelector selector) {
        if (selector == null) {
            throw new IllegalArgumentException("selector must not be null");
        }

        if (!mSelector.equals(selector)) {
            // FIXME: We currently have no way of knowing whether the action provider
            // is still needed by the UI.  Unfortunately this means the action provider
            // may leak callbacks until garbage collection occurs.  This may result in
            // media route providers doing more work than necessary in the short term
            // while trying to discover routes that are no longer of interest to the
            // application.  To solve this problem, the action provider will need some
            // indication from the framework that it is being destroyed.
            if (!mSelector.isEmpty()) {
                mRouter.removeCallback(mCallback);
            }
            if (!selector.isEmpty()) {
                mRouter.addCallback(selector, mCallback);
            }
            mSelector = selector;
            refreshRoute();

            if (mButton != null) {
                mButton.setRouteSelector(selector);
            }
        }
    }

    /**
     * Gets the associated media route button, or null if it has not yet been created.
     */
    public MediaRouteButton getMediaRouteButton() {
        return mButton;
    }

    /**
     * Called when the media route button is being created.
     */
    @SuppressWarnings("deprecation")
    public MediaRouteButton onCreateMediaRouteButton() {
        if (mButton != null) {
            Log.e(TAG, "onCreateMediaRouteButton: This ActionProvider is already associated "
                    + "with a menu item. Don't reuse MediaRouteActionProvider instances!  "
                    + "Abandoning the old button...");
        }

        mButton = new MediaRouteButton(getContext());
        mButton.setCheatSheetEnabled(true);
        mButton.setRouteSelector(mSelector);
        mButton.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.FILL_PARENT));
        return mButton;
    }

    @Override
    public View onCreateActionView() {
        if (mButton != null) {
            Log.e(TAG, "onCreateActionView: this ActionProvider is already associated " +
                    "with a menu item. Don't reuse MediaRouteActionProvider instances! " +
                    "Abandoning the old menu item...");
        }

        mButton = onCreateMediaRouteButton();
        return mButton;
    }

    @Override
    public boolean onPerformDefaultAction() {
        if (mButton != null) {
            return mButton.showDialog();
        }
        return false;
    }

    @Override
    public boolean overridesItemVisibility() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return mRouter.isRouteAvailable(mSelector,
                MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE
                | MediaRouter.AVAILABILITY_FLAG_CONSIDER_ACTIVE_SCAN);
    }

    private void refreshRoute() {
        refreshVisibility();
    }

    private static final class MediaRouterCallback extends MediaRouter.Callback {
        private final WeakReference<MediaRouteActionProvider> mProviderWeak;

        public MediaRouterCallback(MediaRouteActionProvider provider) {
            mProviderWeak = new WeakReference<MediaRouteActionProvider>(provider);
        }

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoute(router);
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoute(router);
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoute(router);
        }

        @Override
        public void onProviderAdded(MediaRouter router, MediaRouter.ProviderInfo provider) {
            refreshRoute(router);
        }

        @Override
        public void onProviderRemoved(MediaRouter router, MediaRouter.ProviderInfo provider) {
            refreshRoute(router);
        }

        @Override
        public void onProviderChanged(MediaRouter router, MediaRouter.ProviderInfo provider) {
            refreshRoute(router);
        }

        private void refreshRoute(MediaRouter router) {
            MediaRouteActionProvider provider = mProviderWeak.get();
            if (provider != null) {
                provider.refreshRoute();
            } else {
                router.removeCallback(this);
            }
        }
    }
}
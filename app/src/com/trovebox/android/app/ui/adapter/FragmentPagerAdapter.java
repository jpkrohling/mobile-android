
package com.trovebox.android.app.ui.adapter;

import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.trovebox.android.app.util.CommonUtils;
import com.trovebox.android.app.util.GuiUtils;
import com.trovebox.android.app.util.TrackerUtils;

/**
 * This is adjusted version of android.support.v4.view.FragmentPagerAdapter
 * which calls setMenuVisibility in the handler post to avoid and issue
 * https://github.com/JakeWharton/ActionBarSherlock/issues/887
 * <p>
 * Implementation of {@link android.support.v4.view.PagerAdapter} that
 * represents each page as a {@link Fragment} that is persistently kept in the
 * fragment manager as long as the user can return to the page.
 * <p>
 * This version of the pager is best for use when there are a handful of
 * typically more static fragments to be paged through, such as a set of tabs.
 * The fragment of each page the user visits will be kept in memory, though its
 * view hierarchy may be destroyed when not visible. This can result in using a
 * significant amount of memory since fragment instances can hold on to an
 * arbitrary amount of state. For larger sets of pages, consider
 * {@link FragmentStatePagerAdapter}.
 * <p>
 * When using FragmentPagerAdapter the host ViewPager must have a valid ID set.
 * </p>
 * <p>
 * Subclasses only need to implement {@link #getItem(int)} and
 * {@link #getCount()} to have a working adapter.
 * <p>
 * Here is an example implementation of a pager containing fragments of lists:
 * {@sample
 * development/samples/Support4Demos/src/com/example/android/supportv4/app/
 * FragmentPagerSupport.java complete}
 * <p>
 * The <code>R.layout.fragment_pager</code> resource of the top-level fragment
 * is: {@sample development/samples/Support4Demos/res/layout/fragment_pager.xml
 * complete}
 * <p>
 * The <code>R.layout.fragment_pager_list</code> resource containing each
 * individual fragment's layout is: {@sample
 * development/samples/Support4Demos/res/layout/fragment_pager_list.xml
 * complete}
 */
public abstract class FragmentPagerAdapter extends PagerAdapter {
    private static final String TAG = "FragmentPagerAdapter";
    private static final boolean DEBUG = false;

    private final FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;
    private Fragment mCurrentPrimaryItem = null;
    private Handler handler;

    public FragmentPagerAdapter(FragmentManager fm) {
        mFragmentManager = fm;
        handler = new Handler();
    }

    /**
     * Return the Fragment associated with a specified position.
     */
    public abstract Fragment getItem(int position);

    @Override
    public void startUpdate(ViewGroup container) {
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        final long itemId = getItemId(position);

        // Do we already have this fragment?
        String name = makeFragmentName(container.getId(), itemId);
        Fragment fragment = mFragmentManager.findFragmentByTag(name);
        if (fragment != null) {
            if (DEBUG)
                Log.v(TAG, "Attaching item #" + itemId + ": f=" + fragment);
            mCurTransaction.attach(fragment);
        } else {
            fragment = getItem(position);
            if (DEBUG)
                Log.v(TAG, "Adding item #" + itemId + ": f=" + fragment);
            mCurTransaction.add(container.getId(), fragment,
                    makeFragmentName(container.getId(), itemId));
        }
        if (fragment != mCurrentPrimaryItem) {
            final Fragment f = fragment;
            handler.post(new Runnable() {

                @Override
                public void run() {
                    f.setMenuVisibility(false);
                    f.setUserVisibleHint(false);
                }
            });
        }

        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }
        if (DEBUG)
            Log.v(TAG, "Detaching item #" + getItemId(position) + ": f=" + object
                    + " v=" + ((Fragment) object).getView());
        mCurTransaction.detach((Fragment) object);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        final Fragment fragment = (Fragment) object;
        if (fragment != mCurrentPrimaryItem) {
            if (mCurrentPrimaryItem != null) {
                final Fragment current = mCurrentPrimaryItem;
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        current.setMenuVisibility(false);
                        current.setUserVisibleHint(false);
                    }
                });
            }
            if (fragment != null) {
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        // #442 check to collect stat
                        // TODO add return statement if stat will be rare and
                        // equals to catched errors
                        if (fragment.getFragmentManager() == null
                                && !fragment.getUserVisibleHint())
                        {
                            CommonUtils.debug(TAG,
                                    "setPrimaryItem post: fragment manager is null");
                            TrackerUtils.trackErrorEvent("#442 situation", "initial_check");

                        }
                        // TODO remove try/catch if error will not appear
                        // anymore and return statement will be added above
                        try
                        {
                            fragment.setMenuVisibility(true);
                            fragment.setUserVisibleHint(true);
                        } catch (Exception ex)
                        {
                            GuiUtils.noAlertError(TAG, ex);
                            try
                            {
                                TrackerUtils.trackErrorEvent("#442 situation",
                                        CommonUtils.format(
                                                "isAdded: %1$b; isDetached: %2$b; " +
                                                        "isHidden: %3$b; isRemoving: %4$b; " +
                                                        "isVisible: %1$b",
                                                fragment.isAdded(),
                                                fragment.isDetached(),
                                                fragment.isHidden(),
                                                fragment.isRemoving(),
                                                fragment.isVisible()
                                                )
                                        );
                            } catch (Exception ex2)
                            {
                                GuiUtils.noAlertError(TAG, ex2);
                                TrackerUtils.trackErrorEvent("#442 situation",
                                        "additinal details error");
                            }
                        }
                    }
                });
            }
            mCurrentPrimaryItem = fragment;
        }
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitAllowingStateLoss();
            mCurTransaction = null;
            mFragmentManager.executePendingTransactions();
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return ((Fragment) object).getView() == view;
    }

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {
    }

    /**
     * Return a unique identifier for the item at the given position.
     * <p>
     * The default implementation returns the given position. Subclasses should
     * override this method if the positions of items can change.
     * </p>
     * 
     * @param position Position within this adapter
     * @return Unique identifier for the item at position
     */
    public long getItemId(int position) {
        return position;
    }

    private static String makeFragmentName(int viewId, long id) {
        return "android:switcher:" + viewId + ":" + id;
    }
}
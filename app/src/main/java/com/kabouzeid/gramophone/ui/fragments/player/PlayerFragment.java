package com.kabouzeid.gramophone.ui.fragments.player;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.draggable.RecyclerViewDragDropManager;
import com.h6ah4i.android.widget.advrecyclerview.utils.WrapperAdapterUtils;
import com.kabouzeid.gramophone.R;
import com.kabouzeid.gramophone.adapter.base.MediaEntryViewHolder;
import com.kabouzeid.gramophone.adapter.song.PlayingQueueAdapter;
import com.kabouzeid.gramophone.dialogs.SongShareDialog;
import com.kabouzeid.gramophone.helper.MusicPlayerRemote;
import com.kabouzeid.gramophone.helper.menu.SongMenuHelper;
import com.kabouzeid.gramophone.model.Song;
import com.kabouzeid.gramophone.ui.activities.base.AbsSlidingMusicPanelActivity;
import com.kabouzeid.gramophone.util.ColorUtil;
import com.kabouzeid.gramophone.util.MusicUtil;
import com.kabouzeid.gramophone.util.Util;
import com.kabouzeid.gramophone.util.ViewUtil;
import com.kabouzeid.gramophone.views.WidthFitSquareLayout;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.solovyev.android.views.llm.LinearLayoutManager;

import butterknife.Bind;
import butterknife.ButterKnife;

public class PlayerFragment extends AbsPlayerFragment implements PlayerAlbumCoverFragment.Callbacks, SlidingUpPanelLayout.PanelSlideListener {
    public static final String TAG = PlayerFragment.class.getSimpleName();

    @Bind(R.id.player_toolbar)
    Toolbar toolbar;
    @Bind(R.id.player_sliding_layout)
    SlidingUpPanelLayout slidingUpPanelLayout;
    @Bind(R.id.player_recycler_view)
    RecyclerView recyclerView;
    @Bind(R.id.playing_queue_card)
    CardView playingQueueCard;
    @Bind(R.id.color_background)
    View colorBackground;
    @Bind(R.id.player_queue_sub_header)
    TextView playerQueueSubHeader;


    private int lastColor;

    private PlaybackControlsFragment playbackControlsFragment;
    private PlayerAlbumCoverFragment playerAlbumCoverFragment;

    private LinearLayoutManager layoutManager;

    private PlayingQueueAdapter playingQueueAdapter;

    private RecyclerView.Adapter wrappedAdapter;
    private RecyclerViewDragDropManager recyclerViewDragDropManager;

    private Impl impl;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (Util.isLandscape(getResources())) {
            impl = new LandscapeImpl();
        } else {
            impl = new PortraitImpl();
        }

        View view = inflater.inflate(R.layout.fragment_player, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        impl.init(this);

        setUpPlayerToolbar();
        setUpSubFragments();

        setUpRecyclerView();

        slidingUpPanelLayout.setPanelSlideListener(this);
        slidingUpPanelLayout.setAntiDragView(view.findViewById(R.id.draggable_area));

        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                impl.setUpPanelAndAlbumCoverHeight(PlayerFragment.this);
            }
        });

        // for some reason the xml attribute doesn't get applied here.
        playingQueueCard.setCardBackgroundColor(ColorUtil.resolveColor(getActivity(), R.attr.cardBackgroundColor));
    }

    @Override
    public void onDestroyView() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.release();
            recyclerViewDragDropManager = null;
        }

        if (recyclerView != null) {
            recyclerView.setItemAnimator(null);
            recyclerView.setAdapter(null);
            recyclerView = null;
        }

        if (wrappedAdapter != null) {
            WrapperAdapterUtils.releaseAll(wrappedAdapter);
            wrappedAdapter = null;
        }
        playingQueueAdapter = null;
        layoutManager = null;
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @Override
    public void onPause() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.cancelDrag();
        }
        super.onPause();
    }

    @Override
    public void onPlayingMetaChanged() {
        updateIsFavorite();
        updateCurrentSong();
        updateQueuePosition();
    }

    @Override
    public void onQueueChanged() {
        updateQueue();
    }

    private void updateQueue() {
        playingQueueAdapter.swapDataSet(MusicPlayerRemote.getPlayingQueue(), MusicPlayerRemote.getPosition());
        if (slidingUpPanelLayout.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            resetToCurrentPosition();
        }
    }

    private void updateQueuePosition() {
        playingQueueAdapter.setCurrent(MusicPlayerRemote.getPosition());
        if (slidingUpPanelLayout.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            resetToCurrentPosition();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void updateCurrentSong() {
        impl.updateCurrentSong(this, MusicPlayerRemote.getCurrentSong());
    }

    private void setUpSubFragments() {
        playbackControlsFragment = (PlaybackControlsFragment) getChildFragmentManager().findFragmentById(R.id.playback_controls_fragment);
        playerAlbumCoverFragment = (PlayerAlbumCoverFragment) getChildFragmentManager().findFragmentById(R.id.player_album_cover_fragment);

        playerAlbumCoverFragment.setCallbacks(this);
    }

    private void setUpPlayerToolbar() {
        toolbar.inflateMenu(R.menu.menu_player);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });
        toolbar.setOnMenuItemClickListener(this);
    }

    private void setUpRecyclerView() {
        recyclerViewDragDropManager = new RecyclerViewDragDropManager();
        final GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();

        playingQueueAdapter = new PlayingQueueAdapter(
                ((AppCompatActivity) getActivity()),
                MusicPlayerRemote.getPlayingQueue(),
                R.layout.item_list,
                false,
                null);
        wrappedAdapter = recyclerViewDragDropManager.createWrappedAdapter(playingQueueAdapter);

        layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setChildSize((int) (getResources().getDisplayMetrics().density * 72));

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(wrappedAdapter);
        recyclerView.setItemAnimator(animator);

        recyclerViewDragDropManager.attachRecyclerView(recyclerView);
    }


    private void updateIsFavorite() {
        boolean isFavorite = MusicUtil.isFavorite(getActivity(), MusicPlayerRemote.getCurrentSong());
        Drawable favoriteIcon = Util.getTintedDrawable(getActivity(), isFavorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_outline_white_24dp, ViewUtil.getToolbarIconColor(getActivity(), false));
        toolbar.getMenu().findItem(R.id.action_toggle_favorite)
                .setIcon(favoriteIcon)
                .setTitle(isFavorite ? getString(R.string.action_remove_from_favorites) : getString(R.string.action_add_to_favorites));
    }

    @Override
    @ColorInt
    public int getPaletteColor() {
        return lastColor;
    }

    private void animateColorChange(final int newColor) {
        impl.animateColorChange(this, newColor);
        lastColor = newColor;
    }

    @Override
    protected void toggleFavorite(Song song) {
        super.toggleFavorite(song);
        if (song.id == MusicPlayerRemote.getCurrentSong().id) {
            if (MusicUtil.isFavorite(getActivity(), song)) {
                playerAlbumCoverFragment.showHeartAnimation();
            }
            updateIsFavorite();
        }
    }

    @Override
    public void onShow() {
        playbackControlsFragment.show();
    }

    @Override
    public void onHide() {
        playbackControlsFragment.hide();
        onBackPressed();
    }

    @Override
    public boolean onBackPressed() {
        if (slidingUpPanelLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            slidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            return true;
        }
        return false;
    }

    @Override
    public void onColorChanged(int color) {
        animateColorChange(color);
        playbackControlsFragment.setColor(color);
        getCallbacks().onPaletteColorChanged();
    }

    @Override
    public void onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.getCurrentSong());
    }

    @Override
    public void onPanelSlide(View view, float slide) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float density = getResources().getDisplayMetrics().density;
            playingQueueCard.setCardElevation((6 * slide + 2) * density);
        }
    }

    @Override
    public void onPanelCollapsed(View view) {
        resetToCurrentPosition();
    }

    private void resetToCurrentPosition() {
        recyclerView.stopScroll();
        layoutManager.scrollToPositionWithOffset(MusicPlayerRemote.getPosition() + 1, 0);
    }

    @Override
    public void onPanelExpanded(View view) {

    }

    @Override
    public void onPanelAnchored(View view) {

    }

    @Override
    public void onPanelHidden(View view) {

    }

    interface Impl {
        void init(PlayerFragment fragment);

        void updateCurrentSong(PlayerFragment fragment, Song song);

        void animateColorChange(PlayerFragment fragment, final int newColor);

        void setUpPanelAndAlbumCoverHeight(PlayerFragment fragment);
    }

    private static abstract class BaseImpl implements Impl {
        public AnimatorSet createDefaultColorChangeAnimatorSet(PlayerFragment fragment, int newColor) {
            Animator backgroundAnimator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int topMargin = fragment.getResources().getDimensionPixelSize(R.dimen.status_bar_padding);
                //noinspection ConstantConditions
                int x = (int) (fragment.playbackControlsFragment.playPauseFab.getX() + fragment.playbackControlsFragment.playPauseFab.getWidth() / 2 + fragment.playbackControlsFragment.getView().getX());
                int y = (int) (topMargin + fragment.playbackControlsFragment.playPauseFab.getY() + fragment.playbackControlsFragment.playPauseFab.getHeight() / 2 + fragment.playbackControlsFragment.getView().getY());
                float startRadius = Math.max(fragment.playbackControlsFragment.playPauseFab.getWidth() / 2, fragment.playbackControlsFragment.playPauseFab.getHeight() / 2);
                float endRadius = Math.max(fragment.colorBackground.getWidth(), fragment.colorBackground.getHeight());
                fragment.colorBackground.setBackgroundColor(newColor);
                backgroundAnimator = ViewAnimationUtils.createCircularReveal(fragment.colorBackground, x, y, startRadius, endRadius);
            } else {
                backgroundAnimator = ViewUtil.createBackgroundColorTransition(fragment.colorBackground, fragment.lastColor, newColor);
            }

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.play(backgroundAnimator).with(ViewUtil.createTextColorTransition(fragment.playerQueueSubHeader, fragment.lastColor, newColor));

            animatorSet.setDuration(ViewUtil.PHONOGRAPH_ANIM_TIME);
            return animatorSet;
        }

        @Override
        public void animateColorChange(PlayerFragment fragment, int newColor) {
            if (ThemeSingleton.get().darkTheme) {
                fragment.playerQueueSubHeader.setTextColor(ColorUtil.getSecondaryTextColor(fragment.getActivity(), false));
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static class PortraitImpl extends BaseImpl {
        MediaEntryViewHolder currentSongViewHolder;
        Song currentSong = new Song();

        @Override
        public void init(final PlayerFragment fragment) {
            currentSongViewHolder = new MediaEntryViewHolder(fragment.getView().findViewById(R.id.current_song));

            currentSongViewHolder.separator.setVisibility(View.VISIBLE);
            currentSongViewHolder.shortSeparator.setVisibility(View.GONE);
            currentSongViewHolder.image.setScaleType(ImageView.ScaleType.CENTER);
            currentSongViewHolder.image.setImageDrawable(Util.getTintedDrawable(fragment.getActivity(), R.drawable.ic_volume_up_white_24dp, ColorUtil.resolveColor(fragment.getActivity(), R.attr.icon_color)));
            currentSongViewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // toggle the panel
                    if (fragment.slidingUpPanelLayout.getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                        fragment.slidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                    } else if (fragment.slidingUpPanelLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
                        fragment.slidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                    }
                }
            });
            currentSongViewHolder.menu.setOnClickListener(new SongMenuHelper.OnClickSongMenu((AppCompatActivity) fragment.getActivity()) {
                @Override
                public Song getSong() {
                    return currentSong;
                }

                public int getMenuRes() {
                    return R.menu.menu_item_playing_queue_song;
                }

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.action_remove_from_playing_queue:
                            MusicPlayerRemote.removeFromQueue(MusicPlayerRemote.getPosition());
                            return true;
                        case R.id.action_share:
                            SongShareDialog.create(getSong()).show(fragment.getFragmentManager(), "SONG_SHARE_DIALOG");
                            return true;
                    }
                    return super.onMenuItemClick(item);
                }
            });
        }

        @Override
        public void setUpPanelAndAlbumCoverHeight(PlayerFragment fragment) {
            WidthFitSquareLayout albumCoverContainer = (WidthFitSquareLayout) fragment.getView().findViewById(R.id.album_cover_container);
            int topMargin = fragment.getResources().getDimensionPixelSize(R.dimen.status_bar_padding);

            final int availablePanelHeight = fragment.slidingUpPanelLayout.getHeight() - fragment.getView().findViewById(R.id.player_content).getHeight() + topMargin;
            final int minPanelHeight = (int) fragment.getResources().getDisplayMetrics().density * (72 + 24) + topMargin;
            if (availablePanelHeight < minPanelHeight) {
                albumCoverContainer.getLayoutParams().height = albumCoverContainer.getHeight() - (minPanelHeight - availablePanelHeight);
                albumCoverContainer.forceSquare(false);
            }
            fragment.slidingUpPanelLayout.setPanelHeight(Math.max(minPanelHeight, availablePanelHeight));

            ((AbsSlidingMusicPanelActivity) fragment.getActivity()).setAntiDragView(fragment.slidingUpPanelLayout.findViewById(R.id.player_panel));
        }

        @Override
        public void updateCurrentSong(PlayerFragment fragment, Song song) {
            currentSong = song;
            currentSongViewHolder.title.setText(song.title);
            currentSongViewHolder.text.setText(song.artistName);
        }

        @Override
        public void animateColorChange(PlayerFragment fragment, int newColor) {
            super.animateColorChange(fragment, newColor);

            fragment.slidingUpPanelLayout.setBackgroundColor(fragment.lastColor);

            createDefaultColorChangeAnimatorSet(fragment, newColor).start();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static class LandscapeImpl extends BaseImpl {
        @Override
        public void init(PlayerFragment fragment) {

        }

        @Override
        public void setUpPanelAndAlbumCoverHeight(PlayerFragment fragment) {
            int topMargin = fragment.getResources().getDimensionPixelSize(R.dimen.status_bar_padding);
            int panelHeight = fragment.slidingUpPanelLayout.getHeight() - fragment.playbackControlsFragment.getView().getHeight() + topMargin;
            fragment.slidingUpPanelLayout.setPanelHeight(panelHeight);

            ((AbsSlidingMusicPanelActivity) fragment.getActivity()).setAntiDragView(fragment.slidingUpPanelLayout.findViewById(R.id.player_panel));
        }

        @Override
        public void updateCurrentSong(PlayerFragment fragment, Song song) {
            fragment.toolbar.setTitle(song.title);
            fragment.toolbar.setSubtitle(song.artistName);
        }

        @Override
        public void animateColorChange(PlayerFragment fragment, int newColor) {
            super.animateColorChange(fragment, newColor);

            fragment.slidingUpPanelLayout.setBackgroundColor(fragment.lastColor);

            AnimatorSet animatorSet = createDefaultColorChangeAnimatorSet(fragment, newColor);
            animatorSet.play(ViewUtil.createBackgroundColorTransition(fragment.toolbar, fragment.lastColor, newColor))
                    .with(ViewUtil.createBackgroundColorTransition(fragment.getView().findViewById(R.id.status_bar), ColorUtil.shiftColorDown(fragment.lastColor), ColorUtil.shiftColorDown(newColor)));
            animatorSet.start();
        }
    }
}
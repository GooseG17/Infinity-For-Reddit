package ml.docilealligator.infinityforreddit.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.gson.JsonParseException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RecyclerViewContentScrollingInterface;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.asynctasks.ChangeThemeName;
import ml.docilealligator.infinityforreddit.asynctasks.DeleteTheme;
import ml.docilealligator.infinityforreddit.asynctasks.GetCustomTheme;
import ml.docilealligator.infinityforreddit.asynctasks.InsertCustomTheme;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.CreateThemeBottomSheetFragment;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.CustomThemeOptionsBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.databinding.ActivityCustomThemeListingBinding;
import ml.docilealligator.infinityforreddit.events.RecreateActivityEvent;
import ml.docilealligator.infinityforreddit.fragments.CustomThemeListingFragment;
import ml.docilealligator.infinityforreddit.utils.CustomThemeSharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class CustomThemeListingActivity extends BaseActivity implements
        CustomThemeOptionsBottomSheetFragment.CustomThemeOptionsBottomSheetFragmentListener,
        CreateThemeBottomSheetFragment.SelectBaseThemeBottomSheetFragmentListener,
        RecyclerViewContentScrollingInterface {

    @Inject
    @Named("default")
    SharedPreferences sharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    RedditDataRoomDatabase redditDataRoomDatabase;
    @Inject
    CustomThemeWrapper customThemeWrapper;
    @Inject
    @Named("light_theme")
    SharedPreferences lightThemeSharedPreferences;
    @Inject
    @Named("dark_theme")
    SharedPreferences darkThemeSharedPreferences;
    @Inject
    @Named("amoled_theme")
    SharedPreferences amoledThemeSharedPreferences;
    @Inject
    Executor executor;
    private FragmentManager fragmentManager;
    private SectionsPagerAdapter sectionsPagerAdapter;
    private ActivityCustomThemeListingBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        setImmersiveModeNotApplicable();

        super.onCreate(savedInstanceState);
        binding = ActivityCustomThemeListingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        setSupportActionBar(binding.toolbarCustomizeThemeListingActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        binding.fabCustomThemeListingActivity.setOnClickListener(view -> {
            CreateThemeBottomSheetFragment createThemeBottomSheetFragment = new CreateThemeBottomSheetFragment();
            createThemeBottomSheetFragment.show(getSupportFragmentManager(), createThemeBottomSheetFragment.getTag());
        });

        fragmentManager = getSupportFragmentManager();

        initializeViewPager();
    }

    private void initializeViewPager() {
        sectionsPagerAdapter = new SectionsPagerAdapter(this);
        binding.viewPager2CustomizeThemeListingActivity.setAdapter(sectionsPagerAdapter);
        binding.viewPager2CustomizeThemeListingActivity.setUserInputEnabled(!sharedPreferences.getBoolean(SharedPreferencesUtils.DISABLE_SWIPING_BETWEEN_TABS, false));
        new TabLayoutMediator(binding.tabLayoutCustomizeThemeListingActivity, binding.viewPager2CustomizeThemeListingActivity, (tab, position) -> {
            switch (position) {
                case 0:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.local));
                    break;
                case 1:
                    Utils.setTitleWithCustomFontToTab(typeface, tab, getString(R.string.online));
                    break;
            }
        }).attach();

        binding.viewPager2CustomizeThemeListingActivity.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                binding.fabCustomThemeListingActivity.show();
                if (position == 0) {
                    unlockSwipeRightToGoBack();
                } else {
                    lockSwipeRightToGoBack();
                }
            }
        });

        fixViewPager2Sensitivity(binding.viewPager2CustomizeThemeListingActivity);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return sharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return customThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        binding.coordinatorLayoutCustomThemeListingActivity.setBackgroundColor(customThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutCustomizeThemeListingActivity, binding.collapsingToolbarLayoutCustomizeThemeListingActivity, binding.toolbarCustomizeThemeListingActivity);
        applyFABTheme(binding.fabCustomThemeListingActivity);
        applyTabLayoutTheme(binding.tabLayoutCustomizeThemeListingActivity);
    }

    @Override
    public void changeName(String oldThemeName) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        EditText themeNameEditText = dialogView.findViewById(R.id.name_edit_text_edit_name_dialog);
        themeNameEditText.setText(oldThemeName);
        themeNameEditText.requestFocus();
        Utils.showKeyboard(this, new Handler(), themeNameEditText);
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.edit_theme_name)
                .setView(dialogView)
                .setPositiveButton(R.string.ok, (dialogInterface, i)
                        -> {
                    Utils.hideKeyboard(this);
                    ChangeThemeName.changeThemeName(executor, redditDataRoomDatabase, oldThemeName,
                            themeNameEditText.getText().toString());
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialogInterface -> {
                    Utils.hideKeyboard(this);
                })
                .show();
    }

    @Override
    public void shareTheme(String themeName) {
        GetCustomTheme.getCustomTheme(executor, new Handler(), redditDataRoomDatabase, themeName,
                customTheme -> {
            if (customTheme != null) {
                String jsonModel = customTheme.getJSONModel();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("simple text", jsonModel);
                    clipboard.setPrimaryClip(clip);
                    Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.theme_copied, Snackbar.LENGTH_SHORT).show();
                } else {
                    Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.copy_theme_faied, Snackbar.LENGTH_SHORT).show();
                }
            } else {
                Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.cannot_find_theme, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void delete(String themeName) {
        new MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialogTheme)
                .setTitle(R.string.delete_theme)
                .setMessage(getString(R.string.delete_theme_dialog_message, themeName))
                .setPositiveButton(R.string.yes, (dialogInterface, i)
                        -> DeleteTheme.deleteTheme(executor, new Handler(), redditDataRoomDatabase, themeName, (isLightTheme, isDarkTheme, isAmoledTheme) -> {
                            if (isLightTheme) {
                                CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(
                                        CustomThemeWrapper.getIndigo(CustomThemeListingActivity.this), lightThemeSharedPreferences);
                            }
                            if (isDarkTheme) {
                                CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(
                                        CustomThemeWrapper.getIndigoDark(CustomThemeListingActivity.this), darkThemeSharedPreferences);
                            }
                            if (isAmoledTheme) {
                                CustomThemeSharedPreferencesUtils.insertThemeToSharedPreferences(
                                        CustomThemeWrapper.getIndigoAmoled(CustomThemeListingActivity.this), amoledThemeSharedPreferences);
                            }
                            EventBus.getDefault().post(new RecreateActivityEvent());
                        }))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public void shareTheme(CustomTheme customTheme) {
        if (customTheme != null) {
            String jsonModel = customTheme.getJSONModel();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("simple text", jsonModel);
                clipboard.setPrimaryClip(clip);
                Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.theme_copied, Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.copy_theme_faied, Snackbar.LENGTH_SHORT).show();
            }
        } else {
            Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.cannot_find_theme, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Subscribe
    public void onRecreateActivityEvent(RecreateActivityEvent recreateActivityEvent) {
        ActivityCompat.recreate(this);
    }

    @Override
    public void importTheme() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            // If it does contain data, decide if you can handle the data.
            if (!clipboard.hasPrimaryClip()) {
                Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.no_data_in_clipboard, Snackbar.LENGTH_SHORT).show();
            } else if (clipboard.getPrimaryClipDescription() != null &&
                    !clipboard.getPrimaryClipDescription().hasMimeType("text/*")) {
                // since the clipboard has data but it is not text
                Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.no_data_in_clipboard, Snackbar.LENGTH_SHORT).show();
            } else if (clipboard.getPrimaryClip() != null) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String json = item.coerceToText(this.getApplicationContext()).toString();
                if (!TextUtils.isEmpty(json)) {
                    try {
                        CustomTheme customTheme = CustomTheme.fromJson(json);
                        checkDuplicateAndImportTheme(customTheme, true);
                    } catch (JsonParseException e) {
                        Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.parse_theme_failed, Snackbar.LENGTH_SHORT).show();
                    }
                } else {
                    Snackbar.make(binding.coordinatorLayoutCustomThemeListingActivity, R.string.parse_theme_failed, Snackbar.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void contentScrollUp() {
        binding.fabCustomThemeListingActivity.show();
    }

    @Override
    public void contentScrollDown() {
        binding.fabCustomThemeListingActivity.hide();
    }

    private void checkDuplicateAndImportTheme(CustomTheme customTheme, boolean checkDuplicate) {
        InsertCustomTheme.insertCustomTheme(executor, new Handler(), redditDataRoomDatabase, lightThemeSharedPreferences,
                darkThemeSharedPreferences, amoledThemeSharedPreferences, customTheme, checkDuplicate,
                new InsertCustomTheme.InsertCustomThemeListener() {
                    @Override
                    public void success() {
                        Toast.makeText(CustomThemeListingActivity.this, R.string.import_theme_success, Toast.LENGTH_SHORT).show();
                        EventBus.getDefault().post(new RecreateActivityEvent());
                    }

                    @Override
                    public void duplicate() {
                        new MaterialAlertDialogBuilder(CustomThemeListingActivity.this, R.style.MaterialAlertDialogTheme)
                                .setTitle(R.string.duplicate_theme_name_dialog_title)
                                .setMessage(getString(R.string.duplicate_theme_name_dialog_message, customTheme.name))
                                .setPositiveButton(R.string.rename, (dialogInterface, i)
                                        -> {
                                    View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
                                    EditText themeNameEditText = dialogView.findViewById(R.id.name_edit_text_edit_name_dialog);
                                    themeNameEditText.setText(customTheme.name);
                                    themeNameEditText.requestFocus();
                                    Utils.showKeyboard(CustomThemeListingActivity.this, new Handler(), themeNameEditText);
                                    new MaterialAlertDialogBuilder(CustomThemeListingActivity.this, R.style.MaterialAlertDialogTheme)
                                            .setTitle(R.string.edit_theme_name)
                                            .setView(dialogView)
                                            .setPositiveButton(R.string.ok, (editTextDialogInterface, i1)
                                                    -> {
                                                Utils.hideKeyboard(CustomThemeListingActivity.this);
                                                if (!themeNameEditText.getText().toString().equals("")) {
                                                    customTheme.name = themeNameEditText.getText().toString();
                                                }
                                                checkDuplicateAndImportTheme(customTheme, true);
                                            })
                                            .setNegativeButton(R.string.cancel, null)
                                            .setOnDismissListener(editTextDialogInterface -> {
                                                Utils.hideKeyboard(CustomThemeListingActivity.this);
                                            })
                                            .show();
                                })
                                .setNegativeButton(R.string.override, (dialogInterface, i) -> {
                                    checkDuplicateAndImportTheme(customTheme, false);
                                })
                                .show();
                    }
                });
    }

    private class SectionsPagerAdapter extends FragmentStateAdapter {

        SectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                CustomThemeListingFragment fragment = new CustomThemeListingFragment();
                return fragment;
            }
            CustomThemeListingFragment fragment = new CustomThemeListingFragment();
            return fragment;
        }

        @Nullable
        private Fragment getCurrentFragment() {
            if (fragmentManager == null) {
                return null;
            }
            return fragmentManager.findFragmentByTag("f" + binding.viewPager2CustomizeThemeListingActivity.getCurrentItem());
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }
}

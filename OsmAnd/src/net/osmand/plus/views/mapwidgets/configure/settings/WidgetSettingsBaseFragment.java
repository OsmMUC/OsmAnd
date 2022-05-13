package net.osmand.plus.views.mapwidgets.configure.settings;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.utils.UiUtilities.DialogButtonType;
import net.osmand.plus.views.mapwidgets.WidgetParams;
import net.osmand.util.Algorithms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

public abstract class WidgetSettingsBaseFragment extends BaseOsmAndFragment {

	private static final String KEY_APP_MODE = "app_mode";

	protected OsmandApplication app;
	protected OsmandSettings settings;
	protected ApplicationMode appMode;
	protected boolean nightMode;

	protected View view;

	@NonNull
	public abstract WidgetParams getWidget();

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = requireMyApplication();
		settings = app.getSettings();
		nightMode = !settings.isLightContent();

		Bundle args = getArguments();
		if (savedInstanceState != null) {
			initParams(savedInstanceState);
		} else if (args != null) {
			initParams(args);
		}
	}

	protected void initParams(@NonNull Bundle bundle) {
		appMode = ApplicationMode.valueOfStringKey(bundle.getString(KEY_APP_MODE), settings.getApplicationMode());
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Context context = requireContext();
		LayoutInflater themedInflater = UiUtilities.getInflater(context, nightMode);
		view = themedInflater.inflate(R.layout.base_widget_fragment_layout, container, false);
		AndroidUtils.addStatusBarPadding21v(app, view);

		setupToolbar();
		setupContent(themedInflater, view.findViewById(R.id.main_content));
		setupApplyButton();

		return view;
	}

	private void setupToolbar() {
		WidgetParams widget = getWidget();

		View closeButton = view.findViewById(R.id.close_button);
		closeButton.setOnClickListener(v -> dismiss());

		View helpButton = view.findViewById(R.id.help_button);
		String docsUrl = widget.getDocsUrl();
		if (Algorithms.isEmpty(docsUrl)) {
			AndroidUiHelper.updateVisibility(helpButton, false);
		} else {
			helpButton.setOnClickListener(v -> {
				FragmentActivity activity = getActivity();
				if (activity != null) {
					AndroidUtils.openUrl(activity, Uri.parse(docsUrl), nightMode);
				}
			});
		}

		TextView title = view.findViewById(R.id.title);
		title.setText(widget.titleId);

		TextView subTitle = view.findViewById(R.id.sub_title);
		subTitle.setText(R.string.shared_string_settings);

		ImageView icon = view.findViewById(R.id.icon);
		icon.setImageDrawable(getIcon(widget.getIconId(nightMode)));
	}

	protected abstract void setupContent(@NonNull LayoutInflater themedInflater, @NonNull ViewGroup container);

	private void setupApplyButton() {
		View applyButton = view.findViewById(R.id.dismiss_button);
		applyButton.setOnClickListener(v -> {
			applySettings();
			dismiss();
		});
		UiUtilities.setupDialogButton(nightMode, applyButton, DialogButtonType.PRIMARY, R.string.shared_string_apply);
	}

	protected abstract void applySettings();

	private void dismiss() {
		Activity activity = getActivity();
		if (activity != null) {
			activity.onBackPressed();
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(KEY_APP_MODE, appMode.getStringKey());
	}

	@Override
	public int getStatusBarColorId() {
		AndroidUiHelper.setStatusBarContentColor(getView(), nightMode);
		return nightMode ? R.color.status_bar_color_dark : R.color.activity_background_color_light;
	}

	public static void showFragment(@NonNull FragmentManager fragmentManager,
	                                @NonNull ApplicationMode appMode,
	                                @NonNull WidgetSettingsBaseFragment fragment) {
		String tag = fragment.getClass().getSimpleName();
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, tag)) {
			Bundle args = new Bundle();
			args.putString(KEY_APP_MODE, appMode.getStringKey());
			fragment.setArguments(args);

			fragmentManager.beginTransaction()
					.add(R.id.fragmentContainer, fragment, tag)
					.addToBackStack(tag)
					.commitAllowingStateLoss();
		}
	}
}
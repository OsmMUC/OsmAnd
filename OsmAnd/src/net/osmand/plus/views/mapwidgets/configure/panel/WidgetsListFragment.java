package net.osmand.plus.views.mapwidgets.configure.panel;

import static net.osmand.plus.utils.ColorUtilities.getDefaultIconColor;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.osmand.CallbackWithObject;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.mapwidgets.MapWidgetInfo;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.mapwidgets.configure.reorder.ReorderWidgetsFragment;
import net.osmand.plus.views.mapwidgets.widgetstates.WidgetState;

public class WidgetsListFragment extends Fragment implements OnScrollChangedListener {

	private static final String SELECTED_GROUP_ATTR = "selected_group_key";

	private OsmandApplication app;
	private UiUtilities uiUtilities;
	private MapWidgetRegistry widgetRegistry;

	private WidgetsPanel selectedPanel;
	private ApplicationMode selectedAppMode;

	private View changeOrderListButton;
	private View changeOrderFooterButton;
	private LinearLayout widgetsContainer;

	private boolean nightMode;

	public void setSelectedPanel(@NonNull WidgetsPanel panel) {
		this.selectedPanel = panel;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (OsmandApplication) requireContext().getApplicationContext();
		uiUtilities = app.getUIUtilities();
		widgetRegistry = app.getOsmandMap().getMapLayers().getMapWidgetRegistry();

		OsmandSettings settings = app.getSettings();
		selectedAppMode = settings.getApplicationMode();
		nightMode = !settings.isLightContent();
		if (savedInstanceState != null) {
			selectedPanel = WidgetsPanel.valueOf(savedInstanceState.getString(SELECTED_GROUP_ATTR));
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_widgets_list, container, false);

		widgetsContainer = view.findViewById(R.id.widgets_list);
		changeOrderListButton = view.findViewById(R.id.change_order_button_in_list);
		changeOrderFooterButton = view.findViewById(R.id.change_order_button_in_bottom);

		NestedScrollView scrollView = view.findViewById(R.id.scroll_view);
		scrollView.getViewTreeObserver().addOnScrollChangedListener(this);

		updateContent();

		TextView panelTitle = view.findViewById(R.id.panel_title);
		panelTitle.setText(getString(selectedPanel.getTitleId()));

		setupReorderButton(changeOrderListButton);
		setupReorderButton(changeOrderFooterButton);

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();

		Fragment fragment = getParentFragment();
		if (fragment instanceof ConfigureWidgetsFragment) {
			((ConfigureWidgetsFragment) fragment).setSelectedFragment(this);
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		Fragment fragment = getParentFragment();
		if (fragment instanceof ConfigureWidgetsFragment) {
			((ConfigureWidgetsFragment) fragment).setSelectedFragment(null);
		}
	}

	public void setupReorderButton(@NonNull View view) {
		view.setOnClickListener(v -> {
			FragmentActivity activity = getActivity();
			if (activity != null) {
				ReorderWidgetsFragment.showInstance(this, activity, selectedPanel, selectedAppMode);
			}
		});
		setupListItemBackground(view);
	}

	public void updateContent() {
		widgetsContainer.removeAllViews();

		int profileColor = selectedAppMode.getProfileColor(nightMode);
		int defaultIconColor = getDefaultIconColor(app, nightMode);

		LayoutInflater inflater = UiUtilities.getInflater(getContext(), nightMode);
		for (MapWidgetInfo widgetInfo : widgetRegistry.getWidgetsForPanel(selectedPanel)) {
			if (!selectedAppMode.isWidgetAvailable(widgetInfo.key)) {
				continue;
			}
			View view = inflater.inflate(R.layout.configure_screen_widget_item, widgetsContainer, false);

			TextView tvTitle = view.findViewById(R.id.title);
			if (widgetInfo.getMessage() != null) {
				tvTitle.setText(widgetInfo.getMessage());
			} else {
				tvTitle.setText(widgetInfo.getMessageId());
			}
			AndroidUiHelper.updateVisibility(view.findViewById(R.id.description), false);

			boolean selected = widgetInfo.isVisibleCollapsed(selectedAppMode) || widgetInfo.isVisible(selectedAppMode);

			int colorId = selected ? profileColor : defaultIconColor;
			ImageView imageView = view.findViewById(R.id.icon);
			imageView.setImageDrawable(uiUtilities.getPaintedIcon(widgetInfo.getSettingsIconId(), colorId));

			ImageView secondaryIcon = view.findViewById(R.id.secondary_icon);
			secondaryIcon.setImageResource(R.drawable.ic_action_additional_option);
			AndroidUiHelper.updateVisibility(secondaryIcon, widgetInfo.getWidgetState() != null);

			CompoundButton compoundButton = view.findViewById(R.id.compound_button);
			compoundButton.setChecked(selected);
			UiUtilities.setupCompoundButton(nightMode, profileColor, compoundButton);

			view.findViewById(R.id.switch_container).setOnClickListener(view1 -> {
				compoundButton.performClick();

				boolean checked = compoundButton.isChecked();
				widgetRegistry.setVisibility(widgetInfo, checked, false);
				imageView.setImageDrawable(uiUtilities.getPaintedIcon(widgetInfo.getSettingsIconId(), checked ? profileColor : defaultIconColor));
			});

			view.setOnClickListener(v -> {
				if (widgetInfo.getWidgetState() == null) {
					boolean checked = !compoundButton.isChecked();
					compoundButton.setChecked(checked);
					widgetRegistry.setVisibility(widgetInfo, checked, false);
					imageView.setImageDrawable(uiUtilities.getPaintedIcon(widgetInfo.getSettingsIconId(), checked ? profileColor : defaultIconColor));
				} else {
					CallbackWithObject<WidgetState> callback = result -> {
						updateContent();
						return true;
					};
					widgetRegistry.showPopUpMenu(view, callback, widgetInfo.getWidgetState(), selectedAppMode,
							null, null, null, compoundButton.isChecked());
				}
			});
			setupListItemBackground(view);
			widgetsContainer.addView(view);
		}
	}

	private void setupListItemBackground(@NonNull View view) {
		View button = view.findViewById(R.id.button_container);
		int activeColor = selectedAppMode.getProfileColor(nightMode);
		Drawable background = UiUtilities.getColoredSelectableDrawable(app, activeColor, 0.3f);
		AndroidUtils.setBackground(button, background);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(SELECTED_GROUP_ATTR, selectedPanel.name());
	}

	@Override
	public void onScrollChanged() {
		int y1 = AndroidUtils.getViewOnScreenY(changeOrderListButton);
		int y2 = AndroidUtils.getViewOnScreenY(changeOrderFooterButton);
		changeOrderFooterButton.setVisibility(y1 <= y2 ? View.GONE : View.VISIBLE);
	}
}
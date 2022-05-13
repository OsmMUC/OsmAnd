package net.osmand.plus;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.osmand.data.FavouritePoint.SpecialPointType;
import net.osmand.data.LatLon;
import net.osmand.plus.AppInitializer.AppInitializeListener;
import net.osmand.plus.AppInitializer.InitEvents;
import net.osmand.plus.api.SettingsAPI;
import net.osmand.plus.mapmarkers.MarkersDb39HelperLegacy;
import net.osmand.plus.myplaces.FavouritesHelper;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.backend.preferences.CommonPreference;
import net.osmand.plus.settings.backend.preferences.EnumStringPreference;
import net.osmand.plus.settings.backend.preferences.OsmandPreference;
import net.osmand.plus.views.layers.RadiusRulerControlLayer.RadiusRulerMode;
import net.osmand.plus.views.mapwidgets.WidgetGroup;
import net.osmand.plus.views.mapwidgets.WidgetParams;
import net.osmand.util.Algorithms;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.AV_DEFAULT_ACTION_AUDIO;
import static net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.AV_DEFAULT_ACTION_CHOOSE;
import static net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.AV_DEFAULT_ACTION_TAKEPICTURE;
import static net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.AV_DEFAULT_ACTION_VIDEO;
import static net.osmand.plus.plugins.audionotes.AudioVideoNotesPlugin.DEFAULT_ACTION_SETTING_ID;
import static net.osmand.plus.views.mapwidgets.MapWidgetRegistry.COLLAPSED_PREFIX;
import static net.osmand.plus.views.mapwidgets.MapWidgetRegistry.HIDE_PREFIX;
import static net.osmand.plus.views.mapwidgets.MapWidgetRegistry.SETTINGS_SEPARATOR;
import static net.osmand.plus.views.mapwidgets.WidgetParams.ARRIVAL_TIME;
import static net.osmand.plus.views.mapwidgets.WidgetParams.AV_NOTES_ON_REQUEST;
import static net.osmand.plus.views.mapwidgets.WidgetParams.AV_NOTES_RECORD_AUDIO;
import static net.osmand.plus.views.mapwidgets.WidgetParams.AV_NOTES_RECORD_VIDEO;
import static net.osmand.plus.views.mapwidgets.WidgetParams.AV_NOTES_TAKE_PHOTO;
import static net.osmand.plus.views.mapwidgets.WidgetParams.AV_NOTES_WIDGET_LEGACY;
import static net.osmand.plus.views.mapwidgets.WidgetParams.BEARING_WIDGET_LEGACY;
import static net.osmand.plus.views.mapwidgets.WidgetParams.INTERMEDIATE_ARRIVAL_TIME;
import static net.osmand.plus.views.mapwidgets.WidgetParams.INTERMEDIATE_TIME_TO_GO;
import static net.osmand.plus.views.mapwidgets.WidgetParams.INTERMEDIATE_TIME_WIDGET_LEGACY;
import static net.osmand.plus.views.mapwidgets.WidgetParams.MAGNETIC_BEARING;
import static net.osmand.plus.views.mapwidgets.WidgetParams.NAVIGATION_TIME_WIDGET_LEGACY;
import static net.osmand.plus.views.mapwidgets.WidgetParams.RELATIVE_BEARING;
import static net.osmand.plus.views.mapwidgets.WidgetParams.TIME_TO_GO;
import static net.osmand.plus.views.mapwidgets.WidgetsPanel.PAGE_SEPARATOR;
import static net.osmand.plus.views.mapwidgets.WidgetsPanel.WIDGET_SEPARATOR;

class AppVersionUpgradeOnInit {

	public static final String FIRST_TIME_APP_RUN = "FIRST_TIME_APP_RUN"; //$NON-NLS-1$
	public static final String VERSION_INSTALLED_NUMBER = "VERSION_INSTALLED_NUMBER"; //$NON-NLS-1$
	public static final String NUMBER_OF_STARTS = "NUMBER_OF_STARTS"; //$NON-NLS-1$
	public static final String FIRST_INSTALLED = "FIRST_INSTALLED"; //$NON-NLS-1$

	// 22 - 2.2
	public static final int VERSION_2_2 = 22;
	// 23 - 2.3
	public static final int VERSION_2_3 = 23;
	// 32 - 3.2
	public static final int VERSION_3_2 = 32;
	// 35 - 3.5
	public static final int VERSION_3_5 = 35;
	// 36 - 3.6
	public static final int VERSION_3_6 = 36;
	// 37 - 3.7
	public static final int VERSION_3_7 = 37;
	// 3701 - 3.7-01 (4 digits version)
	// Each upgrade should have independent version!
	// So, we could have multiple upgrades per 1 release i.e. 3701, 3702, 3703, ... - will be for 3.7
	public static final int VERSION_3_7_01 = 3701;
	// 3800 - 3.8-00
	public static final int VERSION_3_8_00 = 3800;
	// 4000 - 4.0-00
	public static final int VERSION_4_0_00 = 4000;
	// 4001 - 4.0-01
	public static final int VERSION_4_0_01 = 4001;
	// 4002 - 4.0-02
	public static final int VERSION_4_0_02 = 4002;
	// 4003 - 4.0-03 (Migrate state dependent widgets)
	public static final int VERSION_4_0_03 = 4003;
	// 4004 - 4.0-04 (Migrate Radius ruler widget preference)
	public static final int VERSION_4_0_04 = 4004;

	public static final int LAST_APP_VERSION = VERSION_4_0_04;

	static final String VERSION_INSTALLED = "VERSION_INSTALLED";

	private OsmandApplication app;
	private int prevAppVersion;
	private boolean appVersionChanged;
	private boolean firstTime;

	AppVersionUpgradeOnInit(OsmandApplication app) {
		this.app = app;
	}

	@SuppressLint("ApplySharedPref")
	void upgradeVersion(SharedPreferences startPrefs, int lastVersion) {
		if (!startPrefs.contains(NUMBER_OF_STARTS)) {
			startPrefs.edit().putInt(NUMBER_OF_STARTS, 1).commit();
		} else {
			startPrefs.edit().putInt(NUMBER_OF_STARTS, startPrefs.getInt(NUMBER_OF_STARTS, 0) + 1).commit();
		}
		if (!startPrefs.contains(FIRST_INSTALLED)) {
			startPrefs.edit().putLong(FIRST_INSTALLED, System.currentTimeMillis()).commit();
		}
		if (!startPrefs.contains(FIRST_TIME_APP_RUN)) {
			firstTime = true;
			startPrefs.edit().putBoolean(FIRST_TIME_APP_RUN, true).commit();
			startPrefs.edit().putString(VERSION_INSTALLED, Version.getFullVersion(app)).commit();
			startPrefs.edit().putInt(VERSION_INSTALLED_NUMBER, lastVersion).commit();
		} else {
			prevAppVersion = startPrefs.getInt(VERSION_INSTALLED_NUMBER, 0);
			if (needsUpgrade(startPrefs, lastVersion)) {
				OsmandSettings settings = app.getSettings();
				if (prevAppVersion < VERSION_2_2) {
					settings.SHOW_DASHBOARD_ON_START.set(true);
					settings.SHOW_DASHBOARD_ON_MAP_SCREEN.set(true);
					settings.SHOW_CARD_TO_CHOOSE_DRAWER.set(true);
				}
				if (prevAppVersion < VERSION_3_2) {
					settings.BILLING_PURCHASE_TOKENS_SENT.set("");
				}
				if (prevAppVersion < VERSION_3_5 || Version.getAppVersion(app).equals("3.5.3")
						|| Version.getAppVersion(app).equals("3.5.4")) {
					migratePreferences();
					app.getAppInitializer().addListener(new AppInitializeListener() {
						@Override
						public void onStart(AppInitializer init) {

						}

						@Override
						public void onProgress(AppInitializer init, InitEvents event) {
							if (event.equals(InitEvents.FAVORITES_INITIALIZED)) {
								migrateHomeWorkParkingToFavorites();
							}
						}

						@Override
						public void onFinish(AppInitializer init) {
						}
					});
				}
				if (prevAppVersion < VERSION_3_6) {
					migratePreferences();
				}
				if (prevAppVersion < VERSION_3_7) {
					migrateEnumPreferences();
				}
				if (prevAppVersion < VERSION_3_7_01) {
					app.getAppInitializer().addListener(new AppInitializeListener() {
						@Override
						public void onStart(AppInitializer init) {

						}

						@Override
						public void onProgress(AppInitializer init, InitEvents event) {
							if (event.equals(InitEvents.FAVORITES_INITIALIZED)) {
								app.getFavoritesHelper().fixBlackBackground();
							}
						}

						@Override
						public void onFinish(AppInitializer init) {
						}
					});
				}
				if (prevAppVersion < VERSION_3_8_00) {
					migrateQuickActionStates();
				}
				if (prevAppVersion < VERSION_4_0_00) {
					app.getAppInitializer().addListener(new AppInitializeListener() {

						@Override
						public void onStart(AppInitializer init) {
							new MarkersDb39HelperLegacy(app).migrateMarkersGroups();
						}

						@Override
						public void onProgress(AppInitializer init, InitEvents event) {
						}

						@Override
						public void onFinish(AppInitializer init) {
						}
					});
				}
				if (prevAppVersion < VERSION_4_0_03) {
					migrateStateDependentWidgets();
				}
				if (prevAppVersion < VERSION_4_0_04) {
					migrateRadiusRulerWidgetPreference();
				}
				startPrefs.edit().putInt(VERSION_INSTALLED_NUMBER, lastVersion).commit();
				startPrefs.edit().putString(VERSION_INSTALLED, Version.getFullVersion(app)).commit();
				appVersionChanged = true;
			}
		}
	}

	private boolean needsUpgrade(SharedPreferences startPrefs, int maxVersion) {
		return !(Version.getFullVersion(app)).equals(startPrefs.getString(VERSION_INSTALLED, "")) || prevAppVersion < maxVersion;
	}

	boolean isAppVersionChanged() {
		return appVersionChanged;
	}

	int getPrevAppVersion() {
		return prevAppVersion;
	}

	public void resetFirstTimeRun(SharedPreferences startPrefs) {
		if (startPrefs != null) {
			startPrefs.edit().remove(FIRST_TIME_APP_RUN).commit();
		}
	}

	public int getNumberOfStarts(SharedPreferences startPrefs) {
		if (startPrefs == null) {
			return 0;
		}
		return startPrefs.getInt(NUMBER_OF_STARTS, 1);
	}

	public long getFirstInstalledDays(SharedPreferences startPrefs) {
		if (startPrefs == null) {
			return 0;
		}
		long nd = startPrefs.getLong(FIRST_INSTALLED, 0);

		return (System.currentTimeMillis() - nd) / (1000l * 24l * 60l * 60l);
	}

	public boolean isFirstTime() {
		return firstTime;
	}

	public void migratePreferences() {
		OsmandSettings settings = app.getSettings();
		migrateEnumPreferences();
		SharedPreferences globalSharedPreferences = (SharedPreferences) settings.getGlobalPreferences();
		Map<String, ?> globalPrefsMap = globalSharedPreferences.getAll();
		for (Map.Entry<String, ?> entry : globalPrefsMap.entrySet()) {
			String key = entry.getKey();
			OsmandPreference<?> pref = settings.getPreference(key);
			if (pref instanceof CommonPreference) {
				CommonPreference<?> commonPreference = (CommonPreference<?>) pref;
				if (!commonPreference.isGlobal()) {
					for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
						if (!commonPreference.isSetForMode(mode) && !commonPreference.hasDefaultValueForMode(mode)) {
							settings.setPreference(key, entry.getValue(), mode);
						}
					}
				}
			}
		}
		SharedPreferences defaultProfilePreferences = (SharedPreferences) settings.getProfilePreferences(ApplicationMode.DEFAULT);
		Map<String, ?> defaultPrefsMap = defaultProfilePreferences.getAll();
		for (Map.Entry<String, ?> entry : defaultPrefsMap.entrySet()) {
			String key = entry.getKey();
			OsmandPreference<?> pref = settings.getPreference(key);
			if (pref instanceof CommonPreference) {
				CommonPreference<?> commonPreference = (CommonPreference<?>) pref;
				if (commonPreference.isGlobal() && !commonPreference.isSet()) {
					settings.setPreference(key, entry.getValue());
				}
			}
		}
		for (OsmandPreference<?> pref : getGeneralPrefs()) {
			if (pref instanceof CommonPreference) {
				CommonPreference<?> commonPref = (CommonPreference<?>) pref;
				Object defaultVal = commonPref.getModeValue(ApplicationMode.DEFAULT);
				for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
					if (!commonPref.isSetForMode(mode) && !commonPref.hasDefaultValueForMode(mode)) {
						settings.setPreference(commonPref.getId(), defaultVal, mode);
					}
				}
			}
		}
		String json = settings.getSettingsAPI().getString(settings.getGlobalPreferences(), "custom_app_profiles", "");
		if (!Algorithms.isEmpty(json)) {
			Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
			Type t = new TypeToken<ArrayList<ApplicationMode.ApplicationModeBean>>() {
			}.getType();
			List<ApplicationMode.ApplicationModeBean> customProfiles = gson.fromJson(json, t);
			if (!Algorithms.isEmpty(customProfiles)) {
				for (ApplicationMode.ApplicationModeBean modeBean : customProfiles) {
					ApplicationMode.ApplicationModeBuilder builder = ApplicationMode.fromModeBean(app, modeBean);
					ApplicationMode.saveProfile(builder, app);
				}
			}
		}
	}

	public void migrateEnumPreferences() {
		OsmandSettings settings = app.getSettings();
		for (OsmandPreference<?> pref : settings.getRegisteredPreferences().values()) {
			if (pref instanceof EnumStringPreference) {
				EnumStringPreference<?> enumPref = (EnumStringPreference<?>) pref;
				if (enumPref.isGlobal()) {
					migrateEnumPref(enumPref, (SharedPreferences) settings.getGlobalPreferences());
				} else {
					for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
						migrateEnumPref(enumPref, (SharedPreferences) settings.getProfilePreferences(mode));
					}
				}
			}
		}
	}

	public void migrateQuickActionStates() {
		OsmandSettings settings = app.getSettings();
		String quickActionsJson = settings.getSettingsAPI().getString(settings.getGlobalPreferences(), "quick_action_new", "");
		if (!Algorithms.isEmpty(quickActionsJson)) {
			Gson gson = new GsonBuilder().create();
			Type type = new TypeToken<HashMap<String, Boolean>>() {
			}.getType();
			HashMap<String, Boolean> quickActions = gson.fromJson(quickActionsJson, type);
			if (!Algorithms.isEmpty(quickActions)) {
				for (ApplicationMode mode : ApplicationMode.allPossibleValues()) {
					settings.setQuickActions(quickActions, mode);
				}
			}
		}
	}

	private void migrateEnumPref(EnumStringPreference enumPref, SharedPreferences sharedPreferences) {
		Object value = sharedPreferences.getAll().get(enumPref.getId());
		if (value instanceof Integer) {
			int enumIndex = (int) value;
			if (enumIndex >= 0 && enumIndex < enumPref.getValues().length) {
				Enum savedValue = enumPref.getValues()[enumIndex];
				enumPref.setValue(sharedPreferences, savedValue);
			}
		}
	}

	public void migrateHomeWorkParkingToFavorites() {
		OsmandSettings settings = app.getSettings();
		FavouritesHelper favorites = app.getFavoritesHelper();
		SettingsAPI settingsAPI = settings.getSettingsAPI();
		Object globalPreferences = settings.getGlobalPreferences();

		LatLon homePoint = null;
		float lat = settingsAPI.getFloat(globalPreferences, "home_point_lat", 0);
		float lon = settingsAPI.getFloat(globalPreferences, "home_point_lon", 0);
		if (lat != 0 || lon != 0) {
			homePoint = new LatLon(lat, lon);
		}
		LatLon workPoint = null;
		lat = settingsAPI.getFloat(globalPreferences, "work_point_lat", 0);
		lon = settingsAPI.getFloat(globalPreferences, "work_point_lon", 0);
		if (lat != 0 || lon != 0) {
			workPoint = new LatLon(lat, lon);
		}
		if (homePoint != null) {
			favorites.setSpecialPoint(homePoint, SpecialPointType.HOME, null);
		}
		if (workPoint != null) {
			favorites.setSpecialPoint(workPoint, SpecialPointType.WORK, null);
		}
	}


	public OsmandPreference<?>[] getGeneralPrefs() {
		OsmandSettings settings = app.getSettings();
		return new OsmandPreference[] {
				settings.EXTERNAL_INPUT_DEVICE,
				settings.CENTER_POSITION_ON_MAP,
				settings.ROTATE_MAP,
				settings.MAP_SCREEN_ORIENTATION,
				settings.LIVE_MONITORING_URL,
				settings.LIVE_MONITORING_MAX_INTERVAL_TO_SEND,
				settings.LIVE_MONITORING_INTERVAL,
				settings.LIVE_MONITORING,
				settings.SHOW_TRIP_REC_NOTIFICATION,
				settings.AUTO_SPLIT_RECORDING,
				settings.SAVE_TRACK_MIN_SPEED,
				settings.SAVE_TRACK_PRECISION,
				settings.SAVE_TRACK_MIN_DISTANCE,
				settings.SAVE_TRACK_INTERVAL,
				settings.TRACK_STORAGE_DIRECTORY,
				settings.SAVE_HEADING_TO_GPX,
				settings.DISABLE_RECORDING_ONCE_APP_KILLED,
				settings.SAVE_TRACK_TO_GPX,
				settings.SAVE_GLOBAL_TRACK_REMEMBER,
				settings.SAVE_GLOBAL_TRACK_INTERVAL,
				settings.MAP_EMPTY_STATE_ALLOWED,
				settings.DO_NOT_USE_ANIMATIONS,
				settings.USE_KALMAN_FILTER_FOR_COMPASS,
				settings.USE_MAGNETIC_FIELD_SENSOR_COMPASS,
				settings.USE_TRACKBALL_FOR_MOVEMENTS,
				settings.SPEED_SYSTEM,
				settings.ANGULAR_UNITS,
				settings.METRIC_SYSTEM,
				settings.DRIVING_REGION,
				settings.DRIVING_REGION_AUTOMATIC
		};
	}

	private void migrateStateDependentWidgets() {
		OsmandSettings settings = app.getSettings();
		for (ApplicationMode appMode : ApplicationMode.allPossibleValues()) {
			if (settings.MAP_INFO_CONTROLS.isSetForMode(appMode)) {
				replaceWidgetIds(settings.MAP_INFO_CONTROLS, appMode, SETTINGS_SEPARATOR, null);
				hideNotReplacedWidgets(appMode);
			}
			if (settings.RIGHT_WIDGET_PANEL_ORDER.isSetForMode(appMode)) {
				replaceWidgetIds(settings.RIGHT_WIDGET_PANEL_ORDER, appMode, PAGE_SEPARATOR, WIDGET_SEPARATOR);
			}
		}
	}

	private void replaceWidgetIds(@NonNull CommonPreference<String> preference,
	                              @NonNull ApplicationMode appMode,
	                              @NonNull String firstLevelSeparator,
	                              @Nullable String secondLevelSeparator) {
		Map<String, String> replacements = new HashMap<>();
		replacements.put(INTERMEDIATE_TIME_WIDGET_LEGACY, getIntermediateTimeWidgetId(appMode));
		replacements.put(NAVIGATION_TIME_WIDGET_LEGACY, getTimeWidgetId(appMode));
		replacements.put(BEARING_WIDGET_LEGACY, getBearingWidgetId(appMode));
		replacements.put(AV_NOTES_WIDGET_LEGACY, getAudioVideoNotesWidgetId(appMode));

		String oldValue = preference.getModeValue(appMode);
		List<String> firstLevelSplits = new ArrayList<>(Arrays.asList(oldValue.split(firstLevelSeparator)));
		StringBuilder newValue = new StringBuilder();

		for (int i = 0; i < firstLevelSplits.size(); i++) {
			String firstLevelSplit = firstLevelSplits.get(i);
			if (!Algorithms.isEmpty(secondLevelSeparator) && firstLevelSplit.contains(secondLevelSeparator)) {
				List<String> secondLevelSplits = new ArrayList<>(Arrays.asList(firstLevelSplit.split(secondLevelSeparator)));
				for (int j = 0; j < secondLevelSplits.size(); j++) {
					String newWidgetId = getUpdatedWidgetId(secondLevelSplits.get(j), replacements);
					newValue.append(newWidgetId);
					boolean last = j + 1 == secondLevelSplits.size();
					newValue.append(last ? "" : secondLevelSeparator);
				}
				newValue.append(firstLevelSeparator);
			} else {
				String newWidgetId = getUpdatedWidgetId(firstLevelSplit, replacements);
				newValue.append(newWidgetId).append(firstLevelSeparator);
			}
		}

		preference.setModeValue(appMode, newValue.toString());
	}

	@NonNull
	private String getUpdatedWidgetId(@NonNull String widgetId, @NonNull Map<String, String> replacements) {
		String newWidgetId = replacements.get(widgetId);
		if (newWidgetId != null) {
			return newWidgetId;
		}

		if (widgetId.indexOf(COLLAPSED_PREFIX) == 0) {
			String widgetIdNoPredix = widgetId.replaceFirst("\\" + COLLAPSED_PREFIX, "");
			String newWidgetIdNoPrefix = replacements.get(widgetIdNoPredix);
			if (newWidgetIdNoPrefix != null) {
				return newWidgetIdNoPrefix; // Without prefix, collapse is no longer supported
			}
		}

		if (widgetId.indexOf(HIDE_PREFIX) == 0) {
			String widgetIdNoPrefix = widgetId.replaceFirst(HIDE_PREFIX, "");
			String newWidgetIdNoPrefix = replacements.get(widgetIdNoPrefix);
			if (newWidgetIdNoPrefix != null) {
				return HIDE_PREFIX + newWidgetIdNoPrefix;
			}
		}

		return widgetId;
	}

	@NonNull
	private String getIntermediateTimeWidgetId(@NonNull ApplicationMode appMode) {
		boolean intermediateArrival = app.getSettings()
				.SHOW_INTERMEDIATE_ARRIVAL_TIME_OTHERWISE_EXPECTED_TIME.getModeValue(appMode);
		return intermediateArrival
				? INTERMEDIATE_ARRIVAL_TIME.id
				: INTERMEDIATE_TIME_TO_GO.id;
	}

	@NonNull
	private String getTimeWidgetId(@NonNull ApplicationMode appMode) {
		boolean arrival = app.getSettings().SHOW_ARRIVAL_TIME_OTHERWISE_EXPECTED_TIME.getModeValue(appMode);
		return arrival ? ARRIVAL_TIME.id : TIME_TO_GO.id;
	}

	@NonNull
	private String getBearingWidgetId(@NonNull ApplicationMode appMode) {
		boolean relativeBearing = app.getSettings()
				.SHOW_RELATIVE_BEARING_OTHERWISE_REGULAR_BEARING.getModeValue(appMode);
		return relativeBearing ? RELATIVE_BEARING.id : MAGNETIC_BEARING.id;
	}

	@NonNull
	private String getAudioVideoNotesWidgetId(@NonNull ApplicationMode appMode) {
		CommonPreference<Integer> widgetState = app.getSettings()
				.registerIntPreference(DEFAULT_ACTION_SETTING_ID, AV_DEFAULT_ACTION_CHOOSE);
		int audioVideoNotesStateId = widgetState.getModeValue(appMode);
		if (audioVideoNotesStateId == AV_DEFAULT_ACTION_AUDIO) {
			return AV_NOTES_RECORD_AUDIO.id;
		} else if (audioVideoNotesStateId == AV_DEFAULT_ACTION_VIDEO) {
			return AV_NOTES_RECORD_VIDEO.id;
		} else if (audioVideoNotesStateId == AV_DEFAULT_ACTION_TAKEPICTURE) {
			return AV_NOTES_TAKE_PHOTO.id;
		} else {
			return AV_NOTES_ON_REQUEST.id;
		}
	}

	private void hideNotReplacedWidgets(@NonNull ApplicationMode appMode) {
		OsmandSettings settings = app.getSettings();

		String widgetsVisibilityString = settings.MAP_INFO_CONTROLS.getModeValue(appMode);
		List<String> widgetsVisibility = new ArrayList<>(Arrays.asList(widgetsVisibilityString.split(SETTINGS_SEPARATOR)));

		List<WidgetParams> newWidgets = new ArrayList<>();
		newWidgets.addAll(Arrays.asList(INTERMEDIATE_ARRIVAL_TIME, INTERMEDIATE_TIME_TO_GO, ARRIVAL_TIME, TIME_TO_GO));
		newWidgets.addAll(WidgetGroup.BEARING.getWidgets());
		newWidgets.addAll(WidgetGroup.AUDIO_VIDEO_NOTES.getWidgets());

		for (WidgetParams widget : newWidgets) {
			String id = widget.id;
			boolean visibilityDefined = widgetsVisibility.contains(id)
					|| widgetsVisibility.contains(COLLAPSED_PREFIX + id)
					|| widgetsVisibility.contains(HIDE_PREFIX + id);
			if (!visibilityDefined && appMode.isWidgetVisibleByDefault(id)) {
				widgetsVisibility.add(HIDE_PREFIX + id);
			}
		}

		StringBuilder newWidgetsVisibilityString = new StringBuilder();
		for (String widgetVisibility : widgetsVisibility) {
			newWidgetsVisibilityString.append(widgetVisibility).append(SETTINGS_SEPARATOR);
		}
		settings.MAP_INFO_CONTROLS.setModeValue(appMode, newWidgetsVisibilityString.toString());
	}

	private void migrateRadiusRulerWidgetPreference() {
		OsmandSettings settings = app.getSettings();
		CommonPreference<RadiusRulerMode> radiusRulerModePref = new EnumStringPreference<>(settings,
				"ruler_mode", RadiusRulerMode.FIRST, RadiusRulerMode.values()).makeGlobal().makeShared();
		if (radiusRulerModePref.isSet()) {
			RadiusRulerMode radiusRulerMode = radiusRulerModePref.get();
			for (ApplicationMode appMode : ApplicationMode.allPossibleValues()) {
				boolean radiusRulerEnabled = radiusRulerMode == RadiusRulerMode.FIRST
						|| radiusRulerMode == RadiusRulerMode.SECOND;
				boolean radiusRulerNightMode = radiusRulerMode == RadiusRulerMode.SECOND;

				settings.SHOW_RADIUS_RULER_ON_MAP.setModeValue(appMode, radiusRulerEnabled);
				settings.RADIUS_RULER_NIGHT_MODE.setModeValue(appMode, radiusRulerNightMode);
			}
		}
	}
}
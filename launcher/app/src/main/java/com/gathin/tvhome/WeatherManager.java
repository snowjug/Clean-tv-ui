package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Manages automatic IP-based geolocation and Open-Meteo weather updates.
 *
 * Runs fully asynchronously off the main thread, caches responses in SharedPreferences,
 * and updates every 30 minutes while Home is on screen.
 */
public final class WeatherManager {

    public interface Callback {
        void onWeatherUpdated(String weatherText, int effectCode);
    }

    private static final String PREFS_NAME = "weather_cache";
    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_LAST_UPDATE = "last_update";
    private static final String KEY_CACHED_TEXT = "cached_text";
    private static final String KEY_CACHED_EFFECT = "cached_effect";

    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000L; // 30 mins
    private static final long GEO_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L; // 12 hours

    private final Context context;
    private final Callback callback;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private HandlerThread bgThread;
    private Handler bgHandler;
    private boolean isRunning = false;

    private String customCity = "";
    private String unit = "c";

    public WeatherManager(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setOptions(String city, String unit) {
        this.customCity = (city == null) ? "" : city.trim();
        this.unit = (unit == null || !unit.equalsIgnoreCase("f")) ? "c" : "f";
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;

        // Post cached weather immediately to UI so there is no blank delay on launch
        String cachedText = prefs.getString(KEY_CACHED_TEXT, null);
        int cachedEffect = prefs.getInt(KEY_CACHED_EFFECT, WeatherEffectsView.EFFECT_NONE);
        if (cachedText != null && !cachedText.isEmpty()) {
            callback.onWeatherUpdated(cachedText, cachedEffect);
        }

        bgThread = new HandlerThread("weather-updater");
        bgThread.start();
        bgHandler = new Handler(bgThread.getLooper());

        bgHandler.post(this::fetchWeatherTask);
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (bgHandler != null) {
            bgHandler.removeCallbacksAndMessages(null);
            bgHandler = null;
        }
        if (bgThread != null) {
            bgThread.quit();
            bgThread = null;
        }
    }

    private void fetchWeatherTask() {
        if (!isRunning) return;

        try {
            double lat = 0.0;
            double lon = 0.0;
            String cityName = "";

            if (!customCity.isEmpty()) {
                // Custom city geocoding
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                        + java.net.URLEncoder.encode(customCity, "UTF-8")
                        + "&count=1&language=en&format=json";
                String json = httpGet(geoUrl);
                if (json != null) {
                    JSONObject obj = new JSONObject(json);
                    JSONArray results = obj.optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        JSONObject first = results.getJSONObject(0);
                        cityName = first.optString("name", customCity);
                        lat = first.optDouble("latitude", 0.0);
                        lon = first.optDouble("longitude", 0.0);
                    }
                }
            } else {
                // Automatic IP-based Geolocation
                long lastGeo = prefs.getLong("last_geo_time", 0);
                lat = Double.longBitsToDouble(prefs.getLong(KEY_LAT, Double.doubleToLongBits(0.0)));
                lon = Double.longBitsToDouble(prefs.getLong(KEY_LON, Double.doubleToLongBits(0.0)));
                cityName = prefs.getString(KEY_CITY, "");

                if (lat == 0.0 || lon == 0.0 || System.currentTimeMillis() - lastGeo > GEO_REFRESH_INTERVAL_MS) {
                    String ipJson = httpGet("https://ipwho.is/");
                    if (ipJson == null) {
                        ipJson = httpGet("http://ip-api.com/json");
                    }
                    if (ipJson != null) {
                        JSONObject ipObj = new JSONObject(ipJson);
                        if (ipObj.optBoolean("success", true)) {
                            cityName = ipObj.optString("city", "");
                            lat = ipObj.optDouble("latitude", ipObj.optDouble("lat", 0.0));
                            lon = ipObj.optDouble("longitude", ipObj.optDouble("lon", 0.0));

                            prefs.edit()
                                    .putString(KEY_CITY, cityName)
                                    .putLong(KEY_LAT, Double.doubleToRawLongBits(lat))
                                    .putLong(KEY_LON, Double.doubleToRawLongBits(lon))
                                    .putLong("last_geo_time", System.currentTimeMillis())
                                    .apply();
                        }
                    }
                }
            }

            if (lat != 0.0 && lon != 0.0) {
                String tempUnitParam = unit.equalsIgnoreCase("f") ? "&temperature_unit=fahrenheit" : "";
                String weatherUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m"
                                + tempUnitParam + "&timezone=auto",
                        lat, lon);

                String weatherJson = httpGet(weatherUrl);
                if (weatherJson != null) {
                    JSONObject root = new JSONObject(weatherJson);
                    JSONObject current = root.optJSONObject("current");
                    if (current != null) {
                        double temp = current.optDouble("temperature_2m", 0.0);
                        double feelsLike = current.optDouble("apparent_temperature", temp);
                        int weatherCode = current.optInt("weather_code", 0);
                        int isDay = current.optInt("is_day", 1);
                        double wind = current.optDouble("wind_speed_10m", 0.0);
                        double precip = current.optDouble("precipitation", 0.0);

                        WeatherInfo info = parseWeather(weatherCode, isDay, temp, feelsLike, wind, precip, unit);
                        String display = (cityName.isEmpty() ? "" : cityName + "  ·  ")
                                + info.icon + " " + Math.round(temp) + "°" + unit.toUpperCase(Locale.US) + " " + info.condition
                                + "  ·  Feels like " + Math.round(feelsLike) + "°";

                        prefs.edit()
                                .putString(KEY_CACHED_TEXT, display)
                                .putInt(KEY_CACHED_EFFECT, info.effect)
                                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                                .apply();

                        mainHandler.post(() -> callback.onWeatherUpdated(display, info.effect));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Schedule next update in 30 minutes
        if (isRunning && bgHandler != null) {
            bgHandler.postDelayed(this::fetchWeatherTask, REFRESH_INTERVAL_MS);
        }
    }

    private static class WeatherInfo {
        final String condition;
        final String icon;
        final int effect;

        WeatherInfo(String condition, String icon, int effect) {
            this.condition = condition;
            this.icon = icon;
            this.effect = effect;
        }
    }

    private static WeatherInfo parseWeather(int code, int isDay, double temp, double feelsLike, double wind, double precip, String unit) {
        boolean day = (isDay == 1);
        double tempC = unit.equalsIgnoreCase("f") ? (temp - 32) * 5 / 9 : temp;

        if (code == 0) {
            return new WeatherInfo("Clear", day ? "☀️" : "🌙", WeatherEffectsView.EFFECT_NONE);
        } else if (code == 1) {
            return new WeatherInfo("Mainly Clear", day ? "🌤️" : "🌙", WeatherEffectsView.EFFECT_NONE);
        } else if (code == 2) {
            int eff = (wind >= 20) ? WeatherEffectsView.EFFECT_WIND : WeatherEffectsView.EFFECT_NONE;
            return new WeatherInfo("Partly Cloudy", "⛅", eff);
        } else if (code == 3) {
            int eff = (wind >= 18) ? WeatherEffectsView.EFFECT_WIND : WeatherEffectsView.EFFECT_NONE;
            return new WeatherInfo("Overcast", "☁️", eff);
        } else if (code == 45 || code == 48) {
            return new WeatherInfo("Foggy", "🌫️", WeatherEffectsView.EFFECT_WIND);
        } else if (code >= 51 && code <= 57) {
            return new WeatherInfo("Drizzle", "🌦️", WeatherEffectsView.EFFECT_DRIZZLE);
        } else if (code >= 61 && code <= 67) {
            return new WeatherInfo("Rain", "🌧️", WeatherEffectsView.EFFECT_RAIN);
        } else if (code >= 71 && code <= 77) {
            return new WeatherInfo("Snow", "❄️", WeatherEffectsView.EFFECT_SNOW);
        } else if (code >= 80 && code <= 82) {
            return new WeatherInfo("Rain Showers", "🌧️", WeatherEffectsView.EFFECT_RAIN);
        } else if (code >= 85 && code <= 86) {
            return new WeatherInfo("Snow Showers", "🌨️", WeatherEffectsView.EFFECT_SNOW);
        } else if (code >= 95 && code <= 99) {
            return new WeatherInfo("Thunderstorm", "⛈️", WeatherEffectsView.EFFECT_THUNDERSTORM);
        }

        if (tempC <= 3 && precip > 0) {
            return new WeatherInfo("Snow", "❄️", WeatherEffectsView.EFFECT_SNOW);
        }
        if (wind >= 22) {
            return new WeatherInfo("Windy", "🌬️", WeatherEffectsView.EFFECT_WIND);
        }
        return new WeatherInfo("Clear", day ? "☀️" : "🌙", WeatherEffectsView.EFFECT_NONE);
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "CleanTVUI/1.0 (Android TV)");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

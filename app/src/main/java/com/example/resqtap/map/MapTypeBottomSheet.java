package com.example.resqtap.map;
import com.example.resqtap.R;

import com.example.resqtap.utils.UserPrefs;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;


/**
 * MapTypeBottomSheet
 * Bottom sheet untuk pilih jenis peta (Normal, Satellite, Terrain, Hybrid).
 */
public final class MapTypeBottomSheet {
    private MapTypeBottomSheet() {
    }

    /** Paparkan . */
    public static void show(@NonNull Activity activity, GoogleMap map) {
        if (activity.isFinishing()) return;
        if (map == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_map_type, null, false);
        dialog.setContentView(content);

        View btnClose = content.findViewById(R.id.btn_close);
        MaterialCardView cardAuto = content.findViewById(R.id.card_auto);
        MaterialCardView cardStreet = content.findViewById(R.id.card_street);
        MaterialCardView cardSat = content.findViewById(R.id.card_satellite);
        ImageView checkAuto = content.findViewById(R.id.check_auto);
        ImageView checkStreet = content.findViewById(R.id.check_street);
        ImageView checkSat = content.findViewById(R.id.check_satellite);
        SwitchMaterial switchTraffic = content.findViewById(R.id.switch_traffic);
        View cardTraffic = content.findViewById(R.id.card_traffic);

        Runnable applySelectionUi = () -> {
            int type = map.getMapType();
            boolean isSat = (type == GoogleMap.MAP_TYPE_SATELLITE || type == GoogleMap.MAP_TYPE_HYBRID);
            boolean isNormal = !isSat;

            setSelected(cardAuto, checkAuto, false);
            setSelected(cardStreet, checkStreet, false);
            setSelected(cardSat, checkSat, false);

            if (isSat) setSelected(cardSat, checkSat, true);
            else setSelected(cardStreet, checkStreet, true);
        };

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (switchTraffic != null) {
            switchTraffic.setChecked(UserPrefs.isMapTrafficEnabled(activity));
            switchTraffic.setOnCheckedChangeListener((buttonView, isChecked) -> {
                UserPrefs.setMapTrafficEnabled(activity, isChecked);
                try {
                    map.setTrafficEnabled(isChecked);
                } catch (Exception ignored) {
                }
            });
        }
        if (cardTraffic != null) {
            cardTraffic.setOnClickListener(v -> {
                if (switchTraffic != null) switchTraffic.setChecked(!switchTraffic.isChecked());
            });
        }

        if (cardAuto != null) cardAuto.setOnClickListener(v -> {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            applyResQTapMapStyle(activity, map, true);
            UserPrefs.setMapTypeSatellite(activity, false);
            setSelected(cardAuto, checkAuto, true);
            setSelected(cardStreet, checkStreet, false);
            setSelected(cardSat, checkSat, false);
            dialog.dismiss();
        });
        if (cardStreet != null) cardStreet.setOnClickListener(v -> {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            applyResQTapMapStyle(activity, map, true);
            UserPrefs.setMapTypeSatellite(activity, false);
            setSelected(cardAuto, checkAuto, false);
            setSelected(cardStreet, checkStreet, true);
            setSelected(cardSat, checkSat, false);
            dialog.dismiss();
        });
        if (cardSat != null) cardSat.setOnClickListener(v -> {
            map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            applyResQTapMapStyle(activity, map, false);
            UserPrefs.setMapTypeSatellite(activity, true);
            setSelected(cardAuto, checkAuto, false);
            setSelected(cardStreet, checkStreet, false);
            setSelected(cardSat, checkSat, true);
            dialog.dismiss();
        });

        applySelectionUi.run();
        dialog.show();
    }

    private static void applyResQTapMapStyle(Activity activity, GoogleMap map, boolean enabled) {
        if (activity == null || map == null) return;
        try {
            map.setMapStyle(null);
        } catch (Exception ignored) {
        }
    }

    private static void setSelected(MaterialCardView card, ImageView check, boolean selected) {
        if (card != null) {
            int stroke = selected ? R.color.brand_primary : R.color.divider;
            try {
                card.setStrokeColor(ContextCompat.getColor(card.getContext(), stroke));
            } catch (Exception ignored) {
            }
        }
        if (check != null) check.setVisibility(selected ? View.VISIBLE : View.GONE);
    }
}

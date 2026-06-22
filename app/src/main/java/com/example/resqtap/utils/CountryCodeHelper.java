package com.example.resqtap.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.resqtap.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * CountryCodeHelper
 * Membantu pemilihan kod panggilan antarabangsa (country dial code) dan bendera negara untuk input nombor telefon.
 */
public class CountryCodeHelper {

    public static class Country {
        public final String iso;
        public final String name;
        public final String dialCode;
        public final String flag;

        public Country(String iso, String name, String dialCode) {
            this.iso = iso.toUpperCase(Locale.ROOT);
            this.name = name;
            this.dialCode = dialCode.startsWith("+") ? dialCode : "+" + dialCode;
            this.flag = getFlagEmoji(this.iso);
        }

        public String getDisplayName() {
            return name + " (" + dialCode + ")";
        }
    }

    public interface OnCountrySelectedListener {
        void onCountrySelected(Country country);
    }

    /** Menghasilkan emoji bendera negara daripada kod ISO 2 huruf. */
    public static String getFlagEmoji(String countryIso) {
        if (countryIso == null || countryIso.length() != 2) return "🌐";
        String code = countryIso.toUpperCase(Locale.ROOT);
        int firstChar = Character.codePointAt(code, 0) - 'A' + 0x1F1E6;
        int secondChar = Character.codePointAt(code, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }

    /** Senarai lengkap negara dunia dengan kod panggilan (disusun mengikut abjad A-Z). */
    public static List<Country> getAllCountries() {
        List<Country> list = new ArrayList<>();

        list.add(new Country("AF", "Afghanistan", "+93"));
        list.add(new Country("AL", "Albania", "+355"));
        list.add(new Country("DZ", "Algeria", "+213"));
        list.add(new Country("AR", "Argentina", "+54"));
        list.add(new Country("AM", "Armenia", "+374"));
        list.add(new Country("AU", "Australia", "+61"));
        list.add(new Country("AT", "Austria", "+43"));
        list.add(new Country("AZ", "Azerbaijan", "+994"));
        list.add(new Country("BH", "Bahrain", "+973"));
        list.add(new Country("BD", "Bangladesh", "+880"));
        list.add(new Country("BY", "Belarus", "+375"));
        list.add(new Country("BE", "Belgium", "+32"));
        list.add(new Country("BZ", "Belize", "+501"));
        list.add(new Country("BJ", "Benin", "+229"));
        list.add(new Country("BT", "Bhutan", "+975"));
        list.add(new Country("BO", "Bolivia", "+591"));
        list.add(new Country("BA", "Bosnia", "+387"));
        list.add(new Country("BR", "Brazil", "+55"));
        list.add(new Country("BN", "Brunei", "+673"));
        list.add(new Country("BG", "Bulgaria", "+359"));
        list.add(new Country("KH", "Cambodia", "+855"));
        list.add(new Country("CM", "Cameroon", "+237"));
        list.add(new Country("CA", "Canada", "+1"));
        list.add(new Country("CV", "Cabo Verde", "+238"));
        list.add(new Country("KY", "Cayman Islands", "+1345"));
        list.add(new Country("CF", "Central African Republic", "+236"));
        list.add(new Country("TD", "Chad", "+235"));
        list.add(new Country("CL", "Chile", "+56"));
        list.add(new Country("CN", "China", "+86"));
        list.add(new Country("CX", "Christmas Island", "+61"));
        list.add(new Country("CC", "Cocos Island", "+61"));
        list.add(new Country("CO", "Colombia", "+57"));
        list.add(new Country("KM", "Comoros", "+269"));
        list.add(new Country("CK", "Cook Islands", "+682"));
        list.add(new Country("CR", "Costa Rica", "+506"));
        list.add(new Country("HR", "Croatia", "+385"));
        list.add(new Country("CU", "Cuba", "+53"));
        list.add(new Country("CY", "Cyprus", "+357"));
        list.add(new Country("CZ", "Czech Republic", "+420"));
        list.add(new Country("DK", "Denmark", "+45"));
        list.add(new Country("EG", "Egypt", "+20"));
        list.add(new Country("FI", "Finland", "+358"));
        list.add(new Country("FR", "France", "+33"));
        list.add(new Country("DE", "Germany", "+49"));
        list.add(new Country("GR", "Greece", "+30"));
        list.add(new Country("HK", "Hong Kong", "+852"));
        list.add(new Country("IN", "India", "+91"));
        list.add(new Country("ID", "Indonesia", "+62"));
        list.add(new Country("IE", "Ireland", "+353"));
        list.add(new Country("IT", "Italy", "+39"));
        list.add(new Country("JP", "Japan", "+81"));
        list.add(new Country("JO", "Jordan", "+962"));
        list.add(new Country("KW", "Kuwait", "+965"));
        list.add(new Country("LA", "Laos", "+856"));
        list.add(new Country("LB", "Lebanon", "+961"));
        list.add(new Country("MV", "Maldives", "+960"));
        list.add(new Country("MY", "Malaysia", "+60"));
        list.add(new Country("MX", "Mexico", "+52"));
        list.add(new Country("MM", "Myanmar", "+95"));
        list.add(new Country("NP", "Nepal", "+977"));
        list.add(new Country("NL", "Netherlands", "+31"));
        list.add(new Country("NZ", "New Zealand", "+64"));
        list.add(new Country("NG", "Nigeria", "+234"));
        list.add(new Country("NO", "Norway", "+47"));
        list.add(new Country("OM", "Oman", "+968"));
        list.add(new Country("PK", "Pakistan", "+92"));
        list.add(new Country("PH", "Philippines", "+63"));
        list.add(new Country("PT", "Portugal", "+351"));
        list.add(new Country("QA", "Qatar", "+974"));
        list.add(new Country("RU", "Russia", "+7"));
        list.add(new Country("SA", "Saudi Arabia", "+966"));
        list.add(new Country("SG", "Singapore", "+65"));
        list.add(new Country("ZA", "South Africa", "+27"));
        list.add(new Country("KR", "South Korea", "+82"));
        list.add(new Country("ES", "Spain", "+34"));
        list.add(new Country("LK", "Sri Lanka", "+94"));
        list.add(new Country("SE", "Sweden", "+46"));
        list.add(new Country("CH", "Switzerland", "+41"));
        list.add(new Country("TW", "Taiwan", "+886"));
        list.add(new Country("TH", "Thailand", "+66"));
        list.add(new Country("TR", "Turkey", "+90"));
        list.add(new Country("AE", "United Arab Emirates", "+971"));
        list.add(new Country("GB", "United Kingdom", "+44"));
        list.add(new Country("US", "United States", "+1"));
        list.add(new Country("VN", "Vietnam", "+84"));

        Collections.sort(list, (c1, c2) -> c1.name.compareToIgnoreCase(c2.name));

        return list;
    }
}

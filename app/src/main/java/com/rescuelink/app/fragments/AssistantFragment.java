package com.rescuelink.app.fragments;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.utils.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Middle tab for citizens: an on-device assistant connected to local emergency data & first-aid guidance. */
public class AssistantFragment extends Fragment {
    private static final String[] SUGGESTIONS = {
            "My report status", "Find stations", "Responders on duty", "Fire", "Medical emergency", "Flood or typhoon", "System summary"
    };
    private static final String[] STAFF_SUGGESTIONS = {
            "Active emergencies", "Responders on duty", "Find stations", "Update a report status", "Chat with a reporter", "Set my duty status", "Manage accounts"
    };
    private static String historyRole = "";

    private static boolean isStaff() {
        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        return me != null && !com.rescuelink.app.models.User.ROLE_CITIZEN.equals(me.getRole());
    }

    private static final List<String[]> history = new ArrayList<>(); // {fromUser("1"/"0"), text}; survives tab switches

    private final Adapter adapter = new Adapter();
    private RecyclerView rv;
    private EditText et;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        super.onViewCreated(v, state);
        rv = v.findViewById(R.id.rv_assistant);
        et = v.findViewById(R.id.et_assistant);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        String roleKey = me == null ? "" : me.getRole() + "/" + me.getUsername();
        if (!roleKey.equals(historyRole)) { history.clear(); historyRole = roleKey; }   // a new account starts a fresh chat
        if (history.isEmpty() && isStaff()) {
            history.add(new String[]{"0", "Hi, I'm the iResponse AI assistant. I'm connected to your local emergency database! Ask me about active emergencies, responders on duty, station hotlines, or response procedures."});
        } else if (history.isEmpty()) {
            history.add(new String[]{"0", "Hi, I'm the iResponse AI assistant. I'm connected to your local emergency database! Ask me about your report status, nearby stations, responders on duty, or first-aid guidelines."});
        }
        adapter.notifyDataSetChanged();
        scrollDown();

        LinearLayout chips = v.findViewById(R.id.assistant_chips);
        for (final String s : isStaff() ? STAFF_SUGGESTIONS : SUGGESTIONS) {
            TextView c = new TextView(requireContext());
            c.setText(s);
            c.setTextColor(0xFF2563EB);
            c.setTextSize(13);
            c.setBackground(UiUtils.rounded(0xFFE3EEFF, UiUtils.dp(requireContext(), 18)));
            c.setPadding(UiUtils.dp(requireContext(), 14), UiUtils.dp(requireContext(), 8), UiUtils.dp(requireContext(), 14), UiUtils.dp(requireContext(), 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(UiUtils.dp(requireContext(), 8));
            c.setLayoutParams(lp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { ask(s); }
            });
            chips.addView(c);
        }

        v.findViewById(R.id.btn_assistant_send).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { send(); }
        });
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView t, int action, KeyEvent e) {
                if (action == EditorInfo.IME_ACTION_SEND) { send(); return true; }
                return false;
            }
        });
    }

    private void send() {
        String t = et.getText().toString().trim();
        if (t.isEmpty()) return;
        et.setText("");
        ask(t);
    }

    private void ask(String text) {
        history.add(new String[]{"1", text});
        history.add(new String[]{"0", reply(text)});
        adapter.notifyDataSetChanged();
        scrollDown();
    }

    private void scrollDown() {
        if (adapter.getItemCount() > 0) rv.scrollToPosition(adapter.getItemCount() - 1);
    }

    /**
     * True when the question contains any of the words. A plain word must match a whole word ("hi" no longer
     * matches "child"), a word ending in * matches any word that starts with it ("bleed*" = bleeding, bleeds),
     * and anything with a space is matched as a phrase.
     */
    private static boolean has(String q, String... words) {
        String[] tokens = q.split("[^\\p{L}\\p{N}']+");
        for (String w : words) {
            if (w.indexOf(' ') >= 0) {
                if (q.contains(w)) return true;
                continue;
            }
            boolean prefix = w.endsWith("*");
            String base = prefix ? w.substring(0, w.length() - 1) : w;
            for (String t : tokens) {
                if (t.isEmpty()) continue;
                if (prefix ? t.startsWith(base) : t.equals(base)) return true;
            }
        }
        return false;
    }

    /** Keyword guidance connected to the DataRepository database. Works offline. */
    static String reply(String text) {
        String q = text.toLowerCase(Locale.ROOT).trim();
        com.rescuelink.app.models.User me = com.rescuelink.app.utils.SessionManager.getCurrentUser();
        boolean staff = isStaff();

        if (q.isEmpty()) return help(staff);

        // 1. The person's own reports.
        if (has(q, "my report", "my reports", "my incident", "my status", "check status", "track*", "submitted report")) {
            if (me == null) return "Sign in to see your reports.";
            List<com.rescuelink.app.models.Incident> list = com.rescuelink.app.data.DataRepository.getIncidentsFor(me.getUsername());
            if (list.isEmpty()) {
                return "You haven't submitted any emergency reports yet. Go to the Home tab to submit one if you need help.";
            }
            StringBuilder sb = new StringBuilder("Here are your recent report(s):\n");
            for (int idx = 0; idx < Math.min(list.size(), 5); idx++) {
                com.rescuelink.app.models.Incident inc = list.get(idx);
                sb.append("\u2022 #").append(inc.getReference()).append(" [").append(inc.getCategory()).append("]: ")
                  .append(inc.getStatus().getLabel());
                if (inc.getResponderName() != null && !inc.getResponderName().isEmpty()) {
                    sb.append(" (Assigned to ").append(inc.getResponderName()).append(")");
                }
                sb.append("\n");
            }
            return sb.toString().trim();
        }

        // 2. Stations and hotlines.
        if (has(q, "station*", "hospital*", "hotline*", "contact number", "phone number", "find station")) {
            List<com.rescuelink.app.models.Station> stations = com.rescuelink.app.data.DataRepository.getStations();
            if (stations.isEmpty()) return "No emergency stations are currently listed in the database.";
            StringBuilder sb = new StringBuilder("Emergency stations:\n");
            for (com.rescuelink.app.models.Station st : stations) {
                sb.append("\u2022 ").append(st.getName()).append(" (").append(st.getCategory()).append(") - ")
                  .append(st.isAvailable() ? "Available" : "Unavailable");
                if (st.getPhone() != null && !st.getPhone().trim().isEmpty()) sb.append(" | Ph: ").append(st.getPhone());
                sb.append("\n");
            }
            return sb.toString().trim();
        }

        // 3. Responders on duty.
        if (has(q, "responder*", "units", "on duty", "who is available", "staff", "active responder")) {
            StringBuilder sb = new StringBuilder("Responders on duty:\n");
            int count = 0;
            for (com.rescuelink.app.models.User u : com.rescuelink.app.data.DataRepository.getUsers()) {
                if (!com.rescuelink.app.models.User.ROLE_RESPONDER.equals(u.getRole()) || !u.isActive()) continue;
                count++;
                String svc = com.rescuelink.app.data.DataRepository.serviceOf(u);
                sb.append("\u2022 ").append(u.getFullName());
                if (!svc.isEmpty()) sb.append(" [").append(svc).append("]");
                sb.append(" - ").append(u.getDutyStatus()).append("\n");
            }
            return count == 0 ? "There are currently no active responders on duty." : sb.toString().trim();
        }

        // 4. Summary.
        if (has(q, "active emergenc*", "summary", "total report*", "total incident*", "active report*", "stats", "statistics", "system summary")) {
            List<com.rescuelink.app.models.Incident> all = staff
                    ? com.rescuelink.app.data.DataRepository.getAllIncidentsFor(me)
                    : me == null ? new ArrayList<com.rescuelink.app.models.Incident>()
                    : com.rescuelink.app.data.DataRepository.getIncidentsFor(me.getUsername());
            int active = 0, closed = 0;
            for (com.rescuelink.app.models.Incident inc : all) {
                if (inc.isActive()) active++;
                else if (inc.getStatus() != com.rescuelink.app.models.IncidentStatus.DRAFT) closed++;
            }
            return (staff ? "Current summary:" : "Your reports:") + "\n\u2022 Active emergencies: " + active
                    + "\n\u2022 Closed: " + closed
                    + "\n\u2022 Registered stations: " + com.rescuelink.app.data.DataRepository.getStations().size();
        }

        if (staff) return staffReply(q);

        // 5. First aid and safety. These come before small talk so an urgent message is never answered with a greeting.
        if (has(q, "fire", "smoke", "burn*", "sunog", "nasusunog"))
            return "Fire: get out and stay out. Stay low under smoke, feel doors before opening, and never use elevators. Call 911 or your local fire station once you're safe, then send a report from Home so responders see your location.";
        if (has(q, "bleed*", "blood", "faint*", "unconscious", "heart", "chok*", "medical", "injur*", "hurt", "ambulance", "seizure*", "breath*", "pain", "poison*", "allergic"))
            return "Medical: call 911 now. Bleeding: press firmly on the wound with clean cloth and don't remove it. Not breathing: start chest compressions (100-120 per minute) if you know how. Choking: give 5 back blows then 5 abdominal thrusts. Stay with the person until help arrives.";
        if (has(q, "police", "robber*", "theft", "thief", "stolen", "crime", "assault*", "threat*", "gun", "holdup", "danger*", "kidnap*", "stalk*"))
            return "Police: move somewhere safe and lock the door if you can. Call 911 and give your exact location, what happened and any description of the people involved. Don't confront anyone. You can also file a report from Home.";
        if (has(q, "flood*", "typhoon", "storm", "landslide", "baha", "heavy rain"))
            return "Flood or typhoon: move to higher ground early, switch off electricity at the main if water is rising, and never walk or drive through floodwater. Keep your phone charged and your go-bag ready. Call 911 or your local DRRMO if you're trapped.";
        if (has(q, "earthquake", "quake", "lindol", "shaking", "tsunami"))
            return "Earthquake: Drop, Cover and Hold On under sturdy furniture, away from windows. Don't run outside while shaking. After it stops, expect aftershocks, check for injuries and gas leaks, and leave damaged buildings. Near the coast, move to high ground at once if the shaking was strong or long.";
        if (has(q, "call", "calling", "voice", "wifi", "wi-fi", "internet", "no load"))
            return "With internet or Wi-Fi, tapping Police, Fire, DRRMO or Medical on Home places a free in-app call to the nearest responder who is online. Without internet, the app uses a normal phone call or SMS if your SIM can. You can change this under Profile > Settings.";
        if (has(q, "report", "submit", "send", "file"))
            return "To report: go to Home, choose the emergency type, confirm your location, add a short description or photo, and send it. Track its status under My reports, where you can also chat with responders.";
        if (has(q, "map", "nearest", "near me", "closest", "direction*"))
            return "Open the Map tab to see nearby stations and responders and get directions to them.";
        if (has(q, "hi", "hello", "hey", "good morning", "good evening", "good afternoon", "who are you", "help"))
            return help(false);

        return "I'm not sure about that one. " + help(false);
    }

    private static String help(boolean staff) {
        if (staff) {
            return "I'm the iResponse assistant. Ask me about:\n\u2022 \"Active emergencies\"\n\u2022 \"Responders on duty\"\n\u2022 \"Find stations\"\n\u2022 Response checklists for fire, medical, police, flood or accounts.";
        }
        return "I'm the iResponse assistant. Ask me about:\n\u2022 \"My report status\"\n\u2022 \"Find stations\"\n\u2022 \"Responders on duty\"\n\u2022 First aid for fire, medical, police, flood or earthquake.\nIn danger, call 911.";
    }

    /** Offline guidance for responders and admins: how to use the app and quick response checklists. */
    private static String staffReply(String q) {
        if (has(q, "duty", "off duty", "busy", "availability"))
            return "Set your duty status from your Dashboard or Profile: On duty when you can take reports, Busy while on a call. Responders are never counted as Off duty. While you are signed in your live location is shared so the Command Center can send the closest responder.";
        if (has(q, "status", "update", "accept", "resolve", "close", "dispatch*"))
            return "Open Tasks (or Reports for admins), tap a report and use the status buttons: accept it, mark en route, on scene, then resolved. Every change is logged and the reporter sees it right away.";
        if (has(q, "chat", "message*", "reporter", "contact", "call", "calling"))
            return "Open the report from Tasks and tap Message reporter to chat, or Call reporter to start a free in-app voice call over Wi-Fi or mobile data. Confirm their exact location and condition, and keep them on the line until you arrive.";
        if (has(q, "map", "station*", "nearest", "direction*", "route", "track*", "live"))
            return "The Map tab shows incidents, stations and responders on duty, and responder pins move live. Tap an incident or station for directions and the live route.";
        if (has(q, "account*", "user*", "create", "disable", "enable", "admin", "manage"))
            return "Admins: open Manage, then Create account to add a responder or admin for a service, or Accounts to enable, disable or remove one. Service admins can only manage their own service's responders.";
        if (has(q, "fire", "smoke", "sunog"))
            return "Fire response: confirm the address and what is burning, ask if anyone is trapped, keep the reporter at a safe distance, and request backup and a water source early. Update the report to En route and On scene as you go.";
        if (has(q, "medical", "ambulance", "injur*", "bleed*", "unconscious", "heart"))
            return "Medical response: confirm the patient's breathing and consciousness, give first-aid instructions to the reporter while en route, note allergies or conditions, and tell the receiving hospital what to expect.";
        if (has(q, "police", "robber*", "theft", "crime", "assault*", "threat*"))
            return "Police response: confirm that the reporter is safe, get descriptions and direction of travel, approach with backup, and do not ask the reporter to confront anyone.";
        if (has(q, "flood*", "typhoon", "storm", "landslide", "earthquake", "quake", "rescue"))
            return "Disaster response: check road access and water level before moving, prioritise trapped people, coordinate with DRRMO, and ask the Command Center to broadcast an alert to affected residents if needed.";
        if (has(q, "alert*", "broadcast*"))
            return "Command Center admins can publish a broadcast from Manage, then Alerts and broadcasts. Citizens see it as an alert.";
        return help(true);
    }

    private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int p) { return "1".equals(history.get(p)[0]) ? 1 : 0; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(type == 1 ? R.layout.item_message_out : R.layout.item_message_in, parent, false);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int p) {
            ((TextView) h.itemView.findViewById(R.id.tv_msg)).setText(history.get(p)[1]);
            h.itemView.findViewById(R.id.tv_time).setVisibility(View.GONE);
            TextView sender = h.itemView.findViewById(R.id.tv_sender);
            if (sender != null) sender.setText("iResponse Assistant");
            TextView ini = h.itemView.findViewById(R.id.tv_msg_initial);
            if (ini != null) ini.setText("AI");
        }

        @Override public int getItemCount() { return history.size(); }
    }
}

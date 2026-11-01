package com.rescuelink.app.fragments;
import java.util.function.Consumer;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.IncidentDetailActivity;
import com.rescuelink.app.activities.ReportActivity;
import com.rescuelink.app.adapters.IncidentAdapter;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;
import com.rescuelink.app.utils.SessionManager;

import java.util.List;

public class ReportsFragment extends Fragment implements com.rescuelink.app.activities.LiveRefresh {
    private IncidentAdapter adapter;
    private View emptyState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reports, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        adapter = new IncidentAdapter(new Consumer<Incident>() {
@Override public void accept(Incident i) {
            Intent intent = new Intent(requireContext(), IncidentDetailActivity.class);
            intent.putExtra("ref", i.getReference());
            startActivity(intent);
        
}
});
        RecyclerView rv = v.findViewById(R.id.rv_reports);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        emptyState = v.findViewById(R.id.empty_reports);
        v.findViewById(R.id.btn_new_report).setOnClickListener(new View.OnClickListener() {
@Override public void onClick(View x) {startActivity(new Intent(requireContext(), ReportActivity.class));
}
});
    }

    @Override
    public void onResume() {
        super.onResume();
        List<Incident> mine = DataRepository.getIncidentsFor(SessionManager.username());
        adapter.setItems(mine);
        emptyState.setVisibility(mine.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onLiveUpdate() { if (isAdded()) onResume(); }
}

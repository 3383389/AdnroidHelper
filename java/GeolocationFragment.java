package com.rosneft.meeting.fragments;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.rosneft.meeting.App;
import com.rosneft.meeting.BuildConfig;
import com.rosneft.meeting.R;
import com.rosneft.meeting.adapters.PlacesAdapter;
import com.rosneft.meeting.databinding.FragmentGeolocationBinding;
import com.rosneft.meeting.interfaces.OnItemClickListener;
import com.rosneft.meeting.models.Place;
import com.rosneft.meeting.network.BaseCallback;
import com.rosneft.meeting.network.request.PlacesRequest;
import com.rosneft.meeting.network.response.ErrorResponse;
import com.rosneft.meeting.network.response.PlacesResponse;
import com.rosneft.meeting.utils.CircleTransform;
import com.rosneft.meeting.utils.Logger;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.List;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by osipovaleks on 06.06.2017,
 * in RMeeting_Tablet.
 */

@RuntimePermissions
public class GeolocationFragment extends BaseFragment
        implements OnMapReadyCallback, BaseCallback<PlacesResponse>,
        OnItemClickListener<Place>, GoogleMap.OnMarkerClickListener {

    public static final int REQUEST_CODE_SETTING = 12345;

    private FragmentGeolocationBinding mBinding;
    private GoogleMap mGoogleMap;
    private Marker mMarker;
    private float mMaxZoomLevel;

    public static GeolocationFragment newInstance() {
        Bundle args = new Bundle();
        GeolocationFragment fragment = new GeolocationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_geolocation, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding.mapView.onCreate(savedInstanceState);
        mBinding.mapView.getMapAsync(this);

        new PlacesRequest(this).load();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mBinding.mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setOnMarkerClickListener(this);
        mMaxZoomLevel = mGoogleMap.getMaxZoomLevel();
        GeolocationFragmentPermissionsDispatcher.enableLocationWithCheck(this);
    }

    @NeedsPermission({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    public void enableLocation() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mGoogleMap.setMyLocationEnabled(true);
    }

    @Override
    public void onResponse(Call<PlacesResponse> call, Response<PlacesResponse> response) {
        PlacesResponse body = response.body();
        if (body != null) {
            setAdapter(body.getPlaces());
            PlacesResponse.removePlaces();
            PlacesResponse.savePlaces(body.getPlaces());
        }
    }

    @Override
    public void onError(Response<PlacesResponse> response, ErrorResponse errorResponse) {
        loadFromDB();
    }

    @Override
    public void onNetworkError(Call<PlacesResponse> call, Throwable t) {
        loadFromDB();
    }

    @Override
    public void onFailure(Call<PlacesResponse> call, Throwable t) {
        loadFromDB();
    }

    @Override
    public void endResponse() {

    }

    private void loadFromDB() {
        List<Place> places = PlacesResponse.loadPlaces();
        if (places == null || places.isEmpty()) {
            return;
        }
        setAdapter(places);
    }

    private void setAdapter(List<Place> places) {
        PlacesAdapter adapter = new PlacesAdapter(R.layout.item_place_layout, places);
        adapter.addOnItemClickListener(this);
        mBinding.recyclerView.setAdapter(adapter);
    }

    @Override
    public void onItemClick(int position, Place item) {
        Logger.log(this.getClass().getSimpleName(), item.toString());
        final LatLng latLng = new LatLng(item.getLat(), item.getLon());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, mMaxZoomLevel / 2);
        mGoogleMap.animateCamera(cameraUpdate);

        if (mMarker != null) {
            mMarker.remove();
        }


        Picasso.with(getContext())
                .load(item.getImage())
                .resize(72, 72)
                .transform(new CircleTransform())
                .into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        mMarker = mGoogleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {
                        mMarker = mGoogleMap.addMarker(new MarkerOptions()
                                .position(latLng));
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                });
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        new AlertDialog.Builder(getContext())
                .setTitle(App.getResString(R.string.geolocation_navigation))
                .setMessage(App.getResString(R.string.geolocation_navigation_message))
                .setNegativeButton(App.getResString(R.string.cancel), null)
                .setPositiveButton(App.getResString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    LatLng position = marker.getPosition();
                                    Uri gmmIntentUri = Uri.parse("google.navigation:q=" + position.latitude + "," + position.longitude);
                                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                                    mapIntent.setPackage("com.google.android.apps.maps");
                                    if (mapIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                                        startActivity(mapIntent);
                                    }
                                } catch (Exception e) {
                                    Logger.printStackTrace(e);
                                }
                            }
                        })
                .setCancelable(true)
                .create()
                .show();
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        GeolocationFragmentPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBinding.mapView.onResume();
    }
//      Works on newer versions google play services
//    @Override
//    public void onStart() {
//        super.onStart();
//        mBinding.mapView.onStart();
//    }
//
//    @Override
//    public void onStop() {
//        super.onStop();
//        mBinding.mapView.onStop();
//    }

    @Override
    public void onPause() {
        super.onPause();
        mBinding.mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBinding.mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mBinding.mapView.onLowMemory();
    }

    @OnShowRationale({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void showRationaleForLocation(final PermissionRequest request) {
        new AlertDialog.Builder(getContext())
                .setTitle(App.getResString(R.string.geolocation))
                .setMessage(App.getResString(R.string.geolocation_message))
                .setCancelable(false)
                .setNegativeButton(App.getResString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                request.cancel();
                            }
                        })
                .setPositiveButton(App.getResString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                request.proceed();
                            }
                        })
                .create()
                .show();
    }

    @OnPermissionDenied({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void onLocationDenied() {
        new AlertDialog.Builder(getContext())
                .setTitle(App.getResString(R.string.geolocation))
                .setMessage(App.getResString(R.string.geolocation_repeat_message))
                .setCancelable(false)
                .setPositiveButton(App.getResString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                GeolocationFragmentPermissionsDispatcher.enableLocationWithCheck(GeolocationFragment.this);
                            }
                        })
                .create()
                .show();
    }

    @OnNeverAskAgain({Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION})
    void onNeverAskAgain() {
        new AlertDialog.Builder(getContext())
                .setTitle(App.getResString(R.string.geolocation))
                .setMessage(App.getResString(R.string.geolocation_message_settings))
                .setCancelable(false)
                .setNegativeButton(App.getResString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                .setPositiveButton(App.getResString(R.string.ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                                startActivityForResult(appSettingsIntent, REQUEST_CODE_SETTING);
                            }
                        })
                .create()
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE_SETTING == requestCode) {
            GeolocationFragmentPermissionsDispatcher.enableLocationWithCheck(GeolocationFragment.this);
        }
    }


}

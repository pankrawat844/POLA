package com.example.pola;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import com.example.pola.models.Threat;
import com.example.pola.models.User;
import com.example.pola.utils.Config;
import com.example.pola.utils.Locator;
import com.example.pola.utils.NotificationUtils;
import com.example.pola.utils.SharedPreference;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import javax.annotation.Nullable;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final String TAG = "MAIN";
    private FirebaseFirestore mFirestore;
    private Locator locator;
    private User user;
    private GoogleMap mMap;
    private boolean locationPermission;
    private BroadcastReceiver mRegistrationBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // checking for type intent filter
            if (intent.getAction().equals(Config.REGISTRATION_COMPLETE)) {
                getUser();
                setUser();
            } else if (intent.getAction().equals(Config.PUSH_NOTIFICATION)) {
                String id = intent.getStringExtra("id");
                String name = intent.getStringExtra("name");
                Log.i(TAG, id);
                showAcceptAlert(name, id);
            }
        }
    };
    private HashMap<String, Marker> markers = new HashMap<String, Marker>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        mFirestore = FirebaseFirestore.getInstance();
        Bundle bundle = getIntent().getExtras();
        //  if bundle is not empty check if app opened from notification
        if (bundle != null) {
            parseBundle(bundle);
            Log.i(TAG, "Bundle not empty");
        }
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        locator = new Locator(this);
        mapFragment.getMapAsync(this);
        init();
        //check locatiom permission...
        locationPermission = checkLocationPermission();

    }

    @Override
    protected void onNewIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        // check if app opened from notification
        if (bundle != null) {
            parseBundle(bundle);
            Log.i(TAG, "Bundle not empty");
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // register GCM registration complete receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(Config.REGISTRATION_COMPLETE));

        // register new push message receiver
        // by doing this, the activity will be notified each time a new message arrives
        LocalBroadcastManager.getInstance(this).registerReceiver(mRegistrationBroadcastReceiver,
                new IntentFilter(Config.PUSH_NOTIFICATION));

        // clear the notification area when the app is opened
        NotificationUtils.clearNotifications(getApplicationContext());
    }

    @Override
    protected void onPause() {
        // umregister receivers
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRegistrationBroadcastReceiver);
        super.onPause();
    }

    private void getLocation() {
        locator.getLocation(new Locator.Listener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onLocationFound(Location location) {
                user.latitude = location.getLatitude();
                user.longitude = location.getLongitude();
            }

            @Override
            public void onLocationNotFound() {

            }
        });

    }

    /**
     * parse bundle from notification and get the threat using id
     * @param bundle
     */
    private void parseBundle(Bundle bundle) {
        if (!bundle.containsKey("payload")) {
            return;
        }
        try {
            JSONObject payload = new JSONObject(bundle.get("payload").toString());
            String id = payload.getString("id");
            getThreat(id);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    /**
     * get threat information from firebase using id
     * @param id threat id
     */
    private void getThreat(String id) {
        mFirestore.collection("users")
                .document(id)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        Threat threat = documentSnapshot.toObject(Threat.class);
                        if (threat.isHandled) {
                            showAlert("This threat is already resolved.");
                            return;
                        }
                        setMarker(threat);
                    }
                });
    }

    /**
     * Sets marker on map using lat long from threat
     * @param threat Threat object
     */
    private void setMarker(final Threat threat) {
        if (markers.containsKey(threat.id)) {
            markers.get(threat.id).remove();
        }
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(new LatLng(threat.latitude, threat.longitude));
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        if (mMap != null) {
            Marker marker = mMap.addMarker(markerOptions);
            marker.setTag(threat);
            mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                @Override
                public boolean onMarkerClick(final Marker marker) {
                    Threat t = (Threat) marker.getTag();
                    Log.i(TAG, "Marker clicked" + t.id);
                    showResolveAlert(t, marker);
                    return false;
                }
            });
        }
    }

    /**
     * Shows alert dialog with message from param
     * @param message
     */
    private void showAlert(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notification")
                .setMessage(message)
                .setPositiveButton("Ok", null)
                .show();
    }

    /**
     * Show alert dialog for accepting threat and ok and cancel buttons
     * @param name
     * @param id
     */
    private void showAcceptAlert(String name, final String id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        final String title = name + " is in threat. Do you want to accept this threat ?";
        builder.setTitle(title);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                getThreat(id);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    /**
     * Show resolve alert with ok and cancel button. Ok will resolve threat and remove marker from map.
     * @param threat
     * @param marker
     */
    private void showResolveAlert(final Threat threat, final Marker marker) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
        final String title = "This threat is resolved. Do you want to remove this threat ?";
        builder.setTitle(title);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mFirestore.collection("users")
                        .document(threat.id)
                        .update("isHandled", true);
                marker.remove();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    /**
     * initial setup on app startup
     */
    private void init() {
        getUser();
        if (user.id == "") {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter your name");

            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            builder.setView(input);

            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getUser();
                    user.name = input.getText().toString();
                    setUser();
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            builder.show();
        } else {
            setUser();
        }
    }

    /**
     * get user information from shared prefs
     */
    private void getUser() {
        user = new User(
                SharedPreference.getInstance(this).getStringValue("id", ""),
                SharedPreference.getInstance(this).getStringValue("name", ""),
                SharedPreference.getInstance(this).getStringValue("token", "")
        );
    }

    /**
     * sets user information from shared prefs
     */
    private void setUser() {
        SharedPreference.getInstance(getApplicationContext())
                .setValue("id", user.id);
        SharedPreference.getInstance(getApplicationContext())
                .setValue("name", user.name);
        if (user.id == "") {
            mFirestore.collection("users")
                    .add(user)
                    .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                        @Override
                        public void onSuccess(DocumentReference documentReference) {
                            user.id = documentReference.getId();
                            SharedPreference.getInstance(getApplicationContext())
                                    .setValue("id", user.id);
                            Log.d(TAG, "DocumentSnapshot added with ID: " + documentReference.getId());
                        }
                    });
        } else {
            Log.i(TAG, user.token);
            mFirestore.collection("users")
                    .document(user.id)
                    .set(user);
        }
    }


    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 99);
            return false;
        } else {
            getLocation();
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 99: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        getLocation();
                        locationPermission = true;
                        mMap.setMyLocationEnabled(true);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(20.5937, 78.9629), 4), 1000, null);
                    }

                } else {
                    showAlert("You need to allow location permission.");
                }
                return;
            }

        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (locationPermission) {
            mMap.setMyLocationEnabled(true);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(20.5937, 78.9629), 4), 1000, null);
        }
    }
}

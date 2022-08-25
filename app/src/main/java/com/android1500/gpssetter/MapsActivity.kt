package com.android1500.gpssetter

import android.Manifest
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android1500.gpssetter.adapter.FavListAdapter
import com.android1500.gpssetter.databinding.ActivityMapsBinding
import com.android1500.gpssetter.room.Favourite
import com.android1500.gpssetter.viewmodel.MainViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.gun0912.tedpermission.coroutine.TedPermission
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@Suppress("NAME_SHADOWING")
@AndroidEntryPoint
class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapClickListener, FavListAdapter.ClickListener{

    private val viewModel by viewModels<MainViewModel>()
    private val binding by lazy {ActivityMapsBinding.inflate(layoutInflater)}
    private lateinit var mMap: GoogleMap
    private lateinit var favListAdapter: FavListAdapter
    private var mMarker: Marker? = null
    private var mLatLng: LatLng? = null
    private var lat: Double? = null
    private var lon: Double? = null
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: AlertDialog

    private val update by lazy {
        viewModel.getAvailableUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        checkLocationPermission()
        setFloatActionButton()
        isModuleEnable()
        updateChecker()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }



    private fun checkLocationPermission(){
        lifecycleScope.launch {
            TedPermission.create()
                .setDeniedTitle("Permission denied")
                .setDeniedMessage(
                    "If you reject permission,you can not use this real location\n\nPlease turn on permissions at [Setting] > [Permission]"
                )
                .setGotoSettingButtonText("Back")
                .setPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .check()
        }
    }
    private fun isModuleEnable(){
        viewModel.isXposed.observe(this){ isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed){
                xposedDialog = MaterialAlertDialogBuilder(this).run {
                    setTitle(R.string.error_xposed_module_missing)
                    setMessage(R.string.error_xposed_module_missing_desc)
                    setCancelable(BuildConfig.DEBUG)
                    show()
                }
            }
        }

    }

    private fun setFloatActionButton() {
        if (viewModel.isStarted) {
            binding.start.visibility = View.GONE
            binding.stop.visibility = View.VISIBLE
        }

        binding.start.setOnClickListener {
            viewModel.update(true,lat!!, lon!!)
            mLatLng?.let {
                mMarker?.position = it
            }
            mMarker?.isVisible = true
            binding.start.visibility = View.GONE
            binding.stop.visibility =View.VISIBLE
            Toast.makeText(this,"Location spoofing start",Toast.LENGTH_LONG).show()
        }
        binding.stop.setOnClickListener {
            mLatLng?.let {
                viewModel.update(false, it.latitude, it.longitude)
            }

                mMarker?.isVisible = false
                binding.stop.visibility = View.GONE
                binding.start.visibility = View.VISIBLE
                Toast.makeText(this,"Location spoofing stop",Toast.LENGTH_LONG).show()

        }
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        val zoom = 12.0f
        lat = viewModel.getLat
        lon  = viewModel.getLng
        mLatLng = LatLng(lat!!, lon!!)
        mLatLng?.let {
            mMarker = mMap.addMarker(
                MarkerOptions().position(it).draggable(false)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
            )
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))

        }
        if (viewModel.isStarted){
            mMarker?.let {
                it.isVisible = true
                it.showInfoWindow()
            }
        }

        mMap.setOnMapClickListener(this)
        if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESS_FINE_LOCATION") == 0) {
            mMap.isMyLocationEnabled = true
        }
        mMap.uiSettings.isCompassEnabled = true


    }

    override fun onMapClick(p0: LatLng) {
            mLatLng = p0
            mMarker?.let { marker ->
                mLatLng?.let {
                    marker.position = it
                    marker.isVisible = true
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(it))
                    lat = it.latitude
                    lon = it.longitude
                }
            }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when(item.itemId){
            R.id.about -> aboutDialog()
            R.id.add_fav -> addFavouriteDialog()
            R.id.get_favourite -> openFavouriteListDialog()
            R.id.search -> searchDialog()

       }
        return super.onOptionsItemSelected(item)
    }

    private fun moveMapToNewLocation(moveNewLocation: Boolean) {
        if (moveNewLocation) {
            mLatLng = LatLng(lat!!, lon!!)
        }
        mLatLng?.let { latLng ->
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0f))
            mMarker?.let {
                it.position = latLng
                it.isVisible = true
                it.showInfoWindow()
            }

        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
    }

    private fun aboutDialog(){


        alertDialog = MaterialAlertDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.about,null)
        val  tittle = view.findViewById<TextView>(R.id.design_about_title)
        val  version = view.findViewById<TextView>(R.id.design_about_version)
        val  info = view.findViewById<TextView>(R.id.design_about_info)
        tittle.text = getString(R.string.app_name)
        version.text = BuildConfig.VERSION_NAME
        info.text = getString(R.string.about_info)
        alertDialog.setView(view)
        alertDialog.show()

    }

    private fun searchDialog(){

        MaterialAlertDialogBuilder(this).apply {
            val view = layoutInflater.inflate(R.layout.search_layout,null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            setTitle("Search")
            setView(view)
            setPositiveButton("Search") { _, _ ->
                val string = editText.text.toString()
                if (string != "") {
                    var addresses: List<Address>? = null
                    try {
                        addresses = Geocoder(applicationContext).getFromLocationName(string, 3)
                    } catch (ignored: Exception) {
                    }
                    addresses?.let { it ->
                        val address: Address = it[0]
                        mLatLng = LatLng(address.latitude, address.longitude)
                        mLatLng?.let {
                            lat = it.latitude
                            lon = it.longitude
                            moveMapToNewLocation(false)
                        }
                    }

                }

            }
            show()
        }


    }

   private fun addFavouriteDialog(){
       val view = layoutInflater.inflate(R.layout.search_layout,null)
       val editText = view.findViewById<EditText>(R.id.search_edittxt)
       alertDialog =  MaterialAlertDialogBuilder(this).apply {
           setTitle("Add favourite")
           setPositiveButton("Add") { _, _ ->
               val s = editText.text.toString()
               if (!mMarker?.isVisible!!){
                   Toast.makeText(this@MapsActivity,"Not location select",Toast.LENGTH_SHORT).show()
               }else{
                   storeFavorite(-1,s, lat!!, lon!!)
               }
           }
           setView(view)
           show()
       }

   }

    private fun openFavouriteListDialog() {
        favListAdapter = FavListAdapter(this)
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(this@MapsActivity)
        alertDialog.setTitle("Favourites")
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()

        }


 private fun storeFavorite(
        slot: Int,
        address: String,
        lat: Double,
        lon: Double
    ): Boolean {
        var slot = slot
        val address: String = address
      if (slot == -1) {
            var i = 0
            while (true) {
              if(getFavorite(i) == null) {
                    slot = i
                    break
                } else {
                    i++
                }
            }
        }
      addFavourite(
         Favourite(id = slot.toLong(), address = address, lat = lat, lng = lon)
     )
        return true
    }



    private fun getFavorite(id: Int): Favourite {
        return viewModel.getFavouriteSingle(id)
    }

    override fun onItemClick(item: Favourite?) {
        item?.let {
            lat = it.lat
            lon = it.lng
            Toast.makeText(applicationContext,it.address,Toast.LENGTH_SHORT).show()
            moveMapToNewLocation(true)
            if (dialog.isShowing) return dialog.dismiss()
        }


    }

    override fun onItemDelete(item: Favourite?) {
        viewModel.deleteFavourite(item!!)
        Toast.makeText(applicationContext,"Delete",Toast.LENGTH_SHORT).show()


    }

    private fun getAllUpdatedFavList(){
        this.lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect { users->
                    favListAdapter.addAllFav(ArrayList(users))
                    favListAdapter.notifyDataSetChanged()
                }
            }
        }

    }
    private fun addFavourite(favourite: Favourite){
        viewModel.insertNewFavourite(favourite)
        viewModel.response.observe(this){
            if (it == (-1).toLong()) Toast.makeText(this, getString(R.string.cant_save), Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, applicationContext.getString(R.string.record_saved), Toast.LENGTH_SHORT).show()

        }
    }

    private fun updateDialog(){
        alertDialog = MaterialAlertDialogBuilder(this@MapsActivity)
        alertDialog.setTitle(R.string.snackbar_update)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton("Update") { _, _ ->
            MaterialAlertDialogBuilder(this).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(this@MapsActivity)
                    dialog.dismiss()
                }
                lifecycleScope.launch {
                    viewModel.downloadState.collect {
                        when (it) {
                            is MainViewModel.State.Downloading -> {
                                if (it.progress > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = it.progress
                                }
                            }
                            is MainViewModel.State.Done -> {
                                viewModel.openPackageInstaller(this@MapsActivity, it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }

                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    this@MapsActivity,
                                    R.string.bs_update_download_failed,
                                    Toast.LENGTH_LONG
                                ).show()

                            }
                            MainViewModel.State.Idle -> TODO()
                        }
                        update?.let { it ->
                            viewModel.startDownload(this@MapsActivity, it)
                        } ?: run {
                            dialog.dismiss()
                        }

                    }
                }
                dialog = create()
                dialog.show()
            }
        }
        dialog = alertDialog.create()
        dialog.show()

    }

    private fun updateChecker(){
        lifecycleScope.launchWhenResumed {
            viewModel.update.collect{
                if (it!= null){
                    updateDialog()
                }
            }
        }
    }







}











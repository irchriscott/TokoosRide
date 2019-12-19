package com.portail.tokoosride.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.portail.tokoosride.R
import com.portail.tokoosride.models.Distance
import com.portail.tokoosride.models.Duration
import com.portail.tokoosride.models.VehicleType
import com.portail.tokoosride.utils.UtilFunctions
import kotlinx.android.synthetic.main.row_vehicle_type_select.view.*

class SelectVehicleTypeAdapter (
    private val vehicleTypes: MutableList<VehicleType>,
    private val distance: Distance,
    private val duration: Duration,
    private val iconsList: MutableList<Drawable>) : RecyclerView.Adapter<SelectVehicleTypeViewHolder>() {

    private var selectedItem: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectVehicleTypeViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val row = layoutInflater.inflate(R.layout.row_vehicle_type_select, parent, false)
        return SelectVehicleTypeViewHolder(row, null, distance, duration)
    }

    override fun getItemCount(): Int = vehicleTypes.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: SelectVehicleTypeViewHolder, position: Int) {
        val type = vehicleTypes[position]
        holder.vehicleType = type

        holder.view.type_name.text = type.name
        holder.view.type_icon.setImageDrawable(iconsList[position])

        val priceClose = UtilFunctions().calculatePriceTransport(distance, duration, type, 0.95)
        val priceFar = UtilFunctions().calculatePriceTransport(distance, duration, type, 1.05)
        holder.view.price_range.text = "$priceClose Fc - $priceFar Fc"

        if(selectedItem == holder.adapterPosition) {
            holder.view.type_icon.background = holder.view.context.resources.getDrawable(R.drawable.bg_selected_image)
            holder.view.type_name.textSize = 21.0f
        } else {
            holder.view.type_icon.background = holder.view.context.resources.getDrawable(R.drawable.bg_unselected_image)
            holder.view.type_name.textSize = 18.0f
        }

        holder.view.setOnClickListener {
            notifyItemChanged(selectedItem)
            selectedItem = holder.adapterPosition
            notifyItemChanged(selectedItem)
        }
    }

    public fun getSelected() : VehicleType = vehicleTypes[selectedItem]
}

class SelectVehicleTypeViewHolder (
    public val view: View,
    var vehicleType: VehicleType? = null,
    distance: Distance, duration: Duration) : RecyclerView.ViewHolder(view) {

    init { }
}
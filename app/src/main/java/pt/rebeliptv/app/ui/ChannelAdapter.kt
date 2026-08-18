package pt.rebeliptv.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import pt.rebeliptv.app.R
import pt.rebeliptv.app.model.Channel

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private val channels = mutableListOf<Channel>()

    fun submitList(items: List<Channel>) {
        channels.clear()
        channels.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_channel,
                parent,
                false
            )

        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ChannelViewHolder,
        position: Int
    ) {
        holder.bind(channels[position])
    }

    override fun getItemCount(): Int {
        return channels.size
    }

    inner class ChannelViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        private val logo: ImageView =
            itemView.findViewById(R.id.channelLogo)

        private val name: TextView =
            itemView.findViewById(R.id.channelName)

        private val category: TextView =
            itemView.findViewById(R.id.channelCategory)

        fun bind(channel: Channel) {
            name.text = channel.name
            category.text = channel.categoryName

            if (channel.logoUrl.isNullOrBlank()) {
                logo.setImageResource(
                    android.R.drawable.ic_menu_gallery
                )
            } else {
                Glide.with(itemView)
                    .load(channel.logoUrl)
                    .placeholder(
                        android.R.drawable.ic_menu_gallery
                    )
                    .error(
                        android.R.drawable.ic_menu_gallery
                    )
                    .into(logo)
            }

            itemView.setOnClickListener {
                onChannelClick(channel)
            }
        }
    }
}

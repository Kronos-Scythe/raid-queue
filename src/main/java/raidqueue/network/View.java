package raidqueue.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import raidqueue.ui.Layout;
import raidqueue.ui.PartyEntry;
import raidqueue.ui.ProgressInfo;
import raidqueue.ui.StatLine;

import java.util.ArrayList;
import java.util.List;

/**
 * The fully-resolved, wire-serializable description of a menu. Built server-side by
 * {@link raidqueue.ui.Menu#open}, sent to the client verbatim, and only ever read (never
 * mutated) once it exists - a fresh View is built and sent every time a menu changes.
 *
 * <p>Grouped into three nested records (rather than one flat record) purely because
 * {@link PacketCodec#tuple} tops out at six fields per call.
 */
public record View(Header header, Entries entries, SidePanels panels) {

    public static final PacketCodec<RegistryByteBuf, View> PACKET_CODEC = PacketCodec.tuple(
        Header.PACKET_CODEC, View::header,
        Entries.PACKET_CODEC, View::entries,
        SidePanels.PACKET_CODEC, View::panels,
        View::new
    );

    /** One clickable (or purely informational) item shown on screen. */
    public record Entry(int slot, ItemStack stack, boolean clickable) {
        public static final PacketCodec<RegistryByteBuf, Entry> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, Entry::slot,
            ItemStack.OPTIONAL_PACKET_CODEC, Entry::stack,
            PacketCodecs.BOOL, Entry::clickable,
            Entry::new
        );
    }

    public record Header(Identifier id, Text title, Text badge, Identifier background, int layout) {
        public static final PacketCodec<RegistryByteBuf, Header> PACKET_CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, Header::id,
            TextCodecs.REGISTRY_PACKET_CODEC, Header::title,
            TextCodecs.REGISTRY_PACKET_CODEC, Header::badge,
            Identifier.PACKET_CODEC, Header::background,
            PacketCodecs.VAR_INT, Header::layout,
            Header::new
        );

        public Layout layoutEnum() {
            Layout[] values = Layout.values();
            return this.layout >= 0 && this.layout < values.length ? values[this.layout] : Layout.GRID;
        }
    }

    public record Entries(List<Entry> info, List<Entry> content, List<Entry> footer, List<Entry> back) {
        private static final PacketCodec<RegistryByteBuf, List<Entry>> LIST_CODEC =
            PacketCodecs.collection(ArrayList::new, Entry.PACKET_CODEC);

        public static final PacketCodec<RegistryByteBuf, Entries> PACKET_CODEC = PacketCodec.tuple(
            LIST_CODEC, Entries::info,
            LIST_CODEC, Entries::content,
            LIST_CODEC, Entries::footer,
            LIST_CODEC, Entries::back,
            Entries::new
        );
    }

    public record SidePanels(List<PartyEntry> party, List<StatLine> stats, ProgressInfo progress) {
        private static final PacketCodec<RegistryByteBuf, PartyEntry> PARTY_ENTRY_CODEC = PacketCodec.tuple(
            ItemStack.OPTIONAL_PACKET_CODEC, PartyEntry::icon,
            TextCodecs.REGISTRY_PACKET_CODEC, PartyEntry::name,
            TextCodecs.REGISTRY_PACKET_CODEC, PartyEntry::line1,
            TextCodecs.REGISTRY_PACKET_CODEC, PartyEntry::line2,
            PartyEntry::new
        );

        private static final PacketCodec<RegistryByteBuf, StatLine> STAT_LINE_CODEC = PacketCodec.tuple(
            TextCodecs.REGISTRY_PACKET_CODEC, StatLine::label,
            TextCodecs.REGISTRY_PACKET_CODEC, StatLine::value,
            StatLine::new
        );

        private static final PacketCodec<RegistryByteBuf, ProgressInfo> PROGRESS_CODEC = PacketCodec.tuple(
            TextCodecs.REGISTRY_PACKET_CODEC, ProgressInfo::title,
            PacketCodecs.VAR_INT, ProgressInfo::current,
            PacketCodecs.VAR_INT, ProgressInfo::max,
            ProgressInfo::new
        );

        public static final PacketCodec<RegistryByteBuf, SidePanels> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.collection(ArrayList::new, PARTY_ENTRY_CODEC), SidePanels::party,
            PacketCodecs.collection(ArrayList::new, STAT_LINE_CODEC), SidePanels::stats,
            PROGRESS_CODEC, SidePanels::progress,
            SidePanels::new
        );
    }
}

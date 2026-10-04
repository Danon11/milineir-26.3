package org.millenaire.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import org.millenaire.village.TravelBookLine;
import org.millenaire.village.TravelBookScreenState;

public record TravelBookContentPayload(
   TravelBookScreenState currentState,
   List<TravelBookLine> lines,
   boolean hasBack,
   boolean hasNext,
   boolean hasPrev,
   String pageTitle,
   boolean titleTranslatable,
   byte mockModelType,
   String mockTexture,
   String mockCloth0,
   String mockCloth1,
   float mockScale,
   String mockHeldItem,
   String mockHeldItemOffHand
) implements CustomPacketPayload {
   private static final int MAX_LINES = 500;
   private static final int MAX_STRING_LENGTH = 512;
   public static final Type<TravelBookContentPayload> TYPE = new Type(ResourceLocation.fromNamespaceAndPath("millenaire", "travel_book_content"));
   public static final StreamCodec<ByteBuf, TravelBookContentPayload> STREAM_CODEC = StreamCodec.of(
      TravelBookContentPayload::encode, TravelBookContentPayload::decode
   );

   public TravelBookContentPayload(
      TravelBookScreenState currentState, List<TravelBookLine> lines, boolean hasBack, boolean hasNext, boolean hasPrev, String pageTitle
   ) {
      this(currentState, lines, hasBack, hasNext, hasPrev, pageTitle, false, (byte)0, "", "", "", 0.0F, "", "");
   }

   public TravelBookContentPayload(
      TravelBookScreenState currentState,
      List<TravelBookLine> lines,
      boolean hasBack,
      boolean hasNext,
      boolean hasPrev,
      String pageTitle,
      boolean titleTranslatable
   ) {
      this(currentState, lines, hasBack, hasNext, hasPrev, pageTitle, titleTranslatable, (byte)0, "", "", "", 0.0F, "", "");
   }

   private static void encode(ByteBuf buf, TravelBookContentPayload payload) {
      ByteBufCodecs.VAR_INT.encode(buf, payload.currentState.ordinal());
      ByteBufCodecs.BOOL.encode(buf, payload.hasBack);
      ByteBufCodecs.BOOL.encode(buf, payload.hasNext);
      ByteBufCodecs.BOOL.encode(buf, payload.hasPrev);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.pageTitle);
      ByteBufCodecs.BOOL.encode(buf, payload.titleTranslatable);
      buf.writeByte(payload.mockModelType);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.mockTexture);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.mockCloth0);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.mockCloth1);
      buf.writeFloat(payload.mockScale);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.mockHeldItem);
      ByteBufCodecs.STRING_UTF8.encode(buf, payload.mockHeldItemOffHand);
      ByteBufCodecs.VAR_INT.encode(buf, payload.lines.size());

      for (TravelBookLine line : payload.lines) {
         encodeLine(buf, line);
      }
   }

   private static void encodeLine(ByteBuf buf, TravelBookLine line) {
      ByteBufCodecs.STRING_UTF8.encode(buf, line.text());
      ByteBufCodecs.BOOL.encode(buf, line.isSeparator());
      ByteBufCodecs.STRING_UTF8.encode(buf, line.leftColumn() != null ? line.leftColumn() : "");
      ByteBufCodecs.STRING_UTF8.encode(buf, line.rightColumn() != null ? line.rightColumn() : "");
      ByteBufCodecs.STRING_UTF8.encode(buf, line.leftIcon() != null ? line.leftIcon() : "");
      ByteBufCodecs.BOOL.encode(buf, line.translatable());
      ByteBufCodecs.STRING_UTF8.encode(buf, line.nativePrefix() != null ? line.nativePrefix() : "");
      boolean hasNav = line.navTarget() != null;
      ByteBufCodecs.BOOL.encode(buf, hasNav);
      if (hasNav) {
         TravelBookLine.TravelBookNavTarget nav = line.navTarget();
         ByteBufCodecs.VAR_INT.encode(buf, nav.targetState().ordinal());
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.cultureKey() != null ? nav.cultureKey() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.categoryKey() != null ? nav.categoryKey() : "");
         ByteBufCodecs.STRING_UTF8.encode(buf, nav.itemKey() != null ? nav.itemKey() : "");
      }
   }

   private static TravelBookContentPayload decode(ByteBuf buf) {
      int stateOrdinal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      TravelBookScreenState[] states = TravelBookScreenState.values();
      TravelBookScreenState state = stateOrdinal >= 0 && stateOrdinal < states.length ? states[stateOrdinal] : TravelBookScreenState.HOME;
      boolean hasBack = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean hasNext = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      boolean hasPrev = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String pageTitle = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      boolean titleTranslatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      byte mockModelType = buf.readByte();
      String mockTexture = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String mockCloth0 = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String mockCloth1 = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      float mockScale = buf.readFloat();
      String mockHeldItem = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      String mockHeldItemOffHand = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      int rawLineCount = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      int lineCount = Math.min(rawLineCount, 500);
      List<TravelBookLine> lines = new ArrayList<>(lineCount);

      for (int i = 0; i < lineCount; i++) {
         lines.add(decodeLine(buf));
      }

      for (int i = lineCount; i < rawLineCount; i++) {
         decodeLine(buf);
      }

      return new TravelBookContentPayload(
         state,
         lines,
         hasBack,
         hasNext,
         hasPrev,
         pageTitle,
         titleTranslatable,
         mockModelType,
         mockTexture,
         mockCloth0,
         mockCloth1,
         mockScale,
         mockHeldItem,
         mockHeldItemOffHand
      );
   }

   private static TravelBookLine decodeLine(ByteBuf buf) {
      String text = truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf));
      boolean isSeparator = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String leftColumn = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
      String rightColumn = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
      String leftIcon = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
      boolean translatable = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      String nativePrefix = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
      boolean hasNav = (Boolean)ByteBufCodecs.BOOL.decode(buf);
      TravelBookLine.TravelBookNavTarget navTarget = null;
      if (hasNav) {
         int navOrdinal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
         TravelBookScreenState[] states = TravelBookScreenState.values();
         TravelBookScreenState navState = navOrdinal >= 0 && navOrdinal < states.length ? states[navOrdinal] : TravelBookScreenState.HOME;
         String cultureKey = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
         String categoryKey = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
         String itemKey = nullIfEmpty(truncate((String)ByteBufCodecs.STRING_UTF8.decode(buf)));
         navTarget = new TravelBookLine.TravelBookNavTarget(navState, cultureKey, categoryKey, itemKey);
      }

      return new TravelBookLine(text, isSeparator, leftColumn, rightColumn, leftIcon, translatable, nativePrefix, navTarget);
   }

   private static String truncate(String raw) {
      return raw.length() > 512 ? raw.substring(0, 512) : raw;
   }

   private static String nullIfEmpty(String s) {
      return s.isEmpty() ? null : s;
   }

   public boolean hasMockVillager() {
      return !this.mockTexture.isEmpty();
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }
}

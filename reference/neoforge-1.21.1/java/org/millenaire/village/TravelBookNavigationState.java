package org.millenaire.village;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class TravelBookNavigationState {
   private static final int MAX_HISTORY = 50;
   private static final Map<UUID, TravelBookNavigationState.PlayerNavState> STATES = new ConcurrentHashMap<>();

   private TravelBookNavigationState() {
   }

   public static void navigate(UUID player, TravelBookScreenState state, String culture, String category, String item) {
      TravelBookNavigationState.PlayerNavState nav = getOrCreate(player);
      if (nav.currentState != null) {
         nav.history.push(new TravelBookNavigationState.NavEntry(nav.currentState, nav.currentCulture, nav.currentCategory, nav.currentItem));
         if (nav.history.size() > 50) {
            nav.history.removeLast();
         }
      }

      nav.currentState = state;
      nav.currentCulture = culture;
      nav.currentCategory = category;
      nav.currentItem = item;
      if (!item.isEmpty() && !nav.currentCategoryItems.contains(item)) {
         nav.currentCategoryItems = List.of();
      }

      updateItemIndex(nav);
   }

   @Nullable
   public static TravelBookNavigationState.NavEntry goBack(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      if (nav != null && !nav.history.isEmpty()) {
         TravelBookNavigationState.NavEntry prev = nav.history.pop();
         nav.currentState = prev.state();
         nav.currentCulture = prev.culture();
         nav.currentCategory = prev.category();
         nav.currentItem = prev.item();
         updateItemIndex(nav);
         return prev;
      } else {
         return null;
      }
   }

   @Nullable
   public static String getNextItem(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      if (nav != null && !nav.currentCategoryItems.isEmpty()) {
         int nextIdx = nav.currentItemIndex + 1;
         return nextIdx >= nav.currentCategoryItems.size() ? null : nav.currentCategoryItems.get(nextIdx);
      } else {
         return null;
      }
   }

   @Nullable
   public static String getPrevItem(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      if (nav != null && !nav.currentCategoryItems.isEmpty()) {
         int prevIdx = nav.currentItemIndex - 1;
         return prevIdx < 0 ? null : nav.currentCategoryItems.get(prevIdx);
      } else {
         return null;
      }
   }

   public static boolean hasNext(UUID player) {
      return getNextItem(player) != null;
   }

   public static boolean hasPrev(UUID player) {
      return getPrevItem(player) != null;
   }

   public static boolean hasBack(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      return nav != null && !nav.history.isEmpty();
   }

   public static void setCurrentCategoryItems(UUID player, List<String> items) {
      TravelBookNavigationState.PlayerNavState nav = getOrCreate(player);
      nav.currentCategoryItems = List.copyOf(items);
      updateItemIndex(nav);
   }

   public static TravelBookScreenState getCurrentState(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      return nav != null ? nav.currentState : TravelBookScreenState.HOME;
   }

   public static String getCurrentCulture(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      return nav != null ? nav.currentCulture : "";
   }

   public static String getCurrentCategory(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      return nav != null ? nav.currentCategory : "";
   }

   public static String getCurrentItem(UUID player) {
      TravelBookNavigationState.PlayerNavState nav = STATES.get(player);
      return nav != null ? nav.currentItem : "";
   }

   public static void clear(UUID player) {
      STATES.remove(player);
   }

   public static void clearAll() {
      STATES.clear();
   }

   private static TravelBookNavigationState.PlayerNavState getOrCreate(UUID player) {
      return STATES.computeIfAbsent(player, k -> new TravelBookNavigationState.PlayerNavState());
   }

   private static void updateItemIndex(TravelBookNavigationState.PlayerNavState nav) {
      if (!nav.currentCategoryItems.isEmpty() && !nav.currentItem.isEmpty()) {
         nav.currentItemIndex = nav.currentCategoryItems.indexOf(nav.currentItem);
      } else {
         nav.currentItemIndex = -1;
      }
   }

   record NavEntry(TravelBookScreenState state, String culture, String category, String item) {
   }

   private static class PlayerNavState {
      final Deque<TravelBookNavigationState.NavEntry> history = new ArrayDeque<>();
      TravelBookScreenState currentState = null;
      String currentCulture = "";
      String currentCategory = "";
      String currentItem = "";
      List<String> currentCategoryItems = List.of();
      int currentItemIndex = -1;
   }
}

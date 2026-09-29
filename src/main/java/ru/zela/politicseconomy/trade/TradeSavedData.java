package ru.zela.politicseconomy.trade;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent state for physical inter-country trade contracts and shipments. */
public final class TradeSavedData extends SavedData {
    public static final String DATA_NAME = "politicseconomy_trade";

    private static final String NEXT_ORDER = "next_order";
    private static final String NEXT_SHIPMENT = "next_shipment";
    private static final String TERMINALS = "terminals";
    private static final String ORDERS = "orders";
    private static final String SHIPMENTS = "shipments";

    private int nextOrder = 1;
    private int nextShipment = 1;

    private final Map<String, Terminal> terminals = new HashMap<>();
    private final Map<Integer, Order> orders = new HashMap<>();
    private final Map<Integer, Shipment> shipments = new HashMap<>();

    public static TradeSavedData create() {
        return new TradeSavedData();
    }

    public static TradeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TradeSavedData data = create();
        data.nextOrder = Math.max(1, tag.getInt(NEXT_ORDER));
        data.nextShipment = Math.max(1, tag.getInt(NEXT_SHIPMENT));

        if (tag.contains(TERMINALS, Tag.TAG_COMPOUND)) {
            CompoundTag terminals = tag.getCompound(TERMINALS);
            for (String country : terminals.getAllKeys()) {
                CompoundTag value = terminals.getCompound(country);
                data.terminals.put(country, Terminal.load(value));
            }
        }

        if (tag.contains(ORDERS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(ORDERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Order order = Order.load(list.getCompound(i));
                data.orders.put(order.id(), order);
            }
        }

        if (tag.contains(SHIPMENTS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(SHIPMENTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Shipment shipment = Shipment.load(list.getCompound(i));
                data.shipments.put(shipment.id(), shipment);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(NEXT_ORDER, nextOrder);
        tag.putInt(NEXT_SHIPMENT, nextShipment);

        CompoundTag terminalTag = new CompoundTag();
        for (Map.Entry<String, Terminal> entry : terminals.entrySet()) {
            terminalTag.put(entry.getKey(), entry.getValue().save());
        }
        tag.put(TERMINALS, terminalTag);

        ListTag orderList = new ListTag();
        for (Order order : orders.values()) {
            orderList.add(order.save());
        }
        tag.put(ORDERS, orderList);

        ListTag shipmentList = new ListTag();
        for (Shipment shipment : shipments.values()) {
            shipmentList.add(shipment.save());
        }
        tag.put(SHIPMENTS, shipmentList);

        return tag;
    }

    public int nextOrderId() {
        return nextOrder++;
    }

    public int nextShipmentId() {
        return nextShipment++;
    }

    public Terminal terminal(String country) {
        return terminals.get(country);
    }

    public void setTerminal(String country, Terminal terminal) {
        terminals.put(country, terminal);
        setDirty();
    }

    public Map<Integer, Order> orders() {
        return Map.copyOf(orders);
    }

    public Order order(int id) {
        return orders.get(id);
    }

    public void putOrder(Order order) {
        orders.put(order.id(), order);
        setDirty();
    }

    public Map<Integer, Shipment> shipments() {
        return Map.copyOf(shipments);
    }

    public Shipment shipment(int id) {
        return shipments.get(id);
    }

    public void putShipment(Shipment shipment) {
        shipments.put(shipment.id(), shipment);
        setDirty();
    }

    public record Terminal(String country, long pos, String dimension) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("country", country);
            tag.putLong("pos", pos);
            tag.putString("dimension", dimension);
            return tag;
        }

        public static Terminal load(CompoundTag tag) {
            return new Terminal(
                tag.getString("country"),
                tag.getLong("pos"),
                tag.getString("dimension")
            );
        }
    }

    public enum OrderStatus {
        OPEN,
        ACCEPTED,
        SHIPPING,
        COMPLETE,
        CANCELLED
    }

    public enum ShipmentStatus {
        WAITING_LOGISTICS,
        IN_TRANSIT,
        DELIVERED
    }

    public static final class Order {
        private final int id;
        private final String buyerCountry;
        private final String itemId;
        private final int quantity;
        private int remaining;
        private final int maxUnitPrice;
        private long reservedFunds;
        private String sellerCountry;
        private int agreedUnitPrice;
        private OrderStatus status;
        private final long createdAt;

        public Order(
            int id,
            String buyerCountry,
            String itemId,
            int quantity,
            int maxUnitPrice,
            long reservedFunds,
            long createdAt
        ) {
            this.id = id;
            this.buyerCountry = buyerCountry;
            this.itemId = itemId;
            this.quantity = quantity;
            this.remaining = quantity;
            this.maxUnitPrice = maxUnitPrice;
            this.reservedFunds = reservedFunds;
            this.status = OrderStatus.OPEN;
            this.createdAt = createdAt;
        }

        public int id() { return id; }
        public String buyerCountry() { return buyerCountry; }
        public String itemId() { return itemId; }
        public int quantity() { return quantity; }
        public int remaining() { return remaining; }
        public int maxUnitPrice() { return maxUnitPrice; }
        public long reservedFunds() { return reservedFunds; }
        public String sellerCountry() { return sellerCountry; }
        public int agreedUnitPrice() { return agreedUnitPrice; }
        public OrderStatus status() { return status; }
        public long createdAt() { return createdAt; }

        public void accept(String sellerCountry, int unitPrice) {
            this.sellerCountry = sellerCountry;
            this.agreedUnitPrice = unitPrice;
            this.status = OrderStatus.ACCEPTED;
        }

        public void shipped(int amount) {
            remaining = Math.max(0, remaining - amount);
            status = remaining <= 0 ? OrderStatus.SHIPPING : OrderStatus.SHIPPING;
        }

        public void complete() {
            remaining = 0;
            status = OrderStatus.COMPLETE;
        }

        public void cancel() {
            status = OrderStatus.CANCELLED;
        }

        public void spendReserved(long amount) {
            reservedFunds = Math.max(0L, reservedFunds - Math.max(0L, amount));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putString("buyer", buyerCountry);
            tag.putString("item", itemId);
            tag.putInt("quantity", quantity);
            tag.putInt("remaining", remaining);
            tag.putInt("max_price", maxUnitPrice);
            tag.putLong("reserved", reservedFunds);
            if (sellerCountry != null) {
                tag.putString("seller", sellerCountry);
            }
            tag.putInt("agreed_price", agreedUnitPrice);
            tag.putString("status", status.name());
            tag.putLong("created", createdAt);
            return tag;
        }

        public static Order load(CompoundTag tag) {
            Order order = new Order(
                tag.getInt("id"),
                tag.getString("buyer"),
                tag.getString("item"),
                tag.getInt("quantity"),
                tag.getInt("max_price"),
                tag.getLong("reserved"),
                tag.getLong("created")
            );
            order.remaining = Math.max(0, tag.getInt("remaining"));
            order.sellerCountry = tag.contains("seller", Tag.TAG_STRING)
                ? tag.getString("seller")
                : null;
            order.agreedUnitPrice = Math.max(0, tag.getInt("agreed_price"));
            try {
                order.status = OrderStatus.valueOf(tag.getString("status"));
            } catch (IllegalArgumentException ignored) {
                order.status = OrderStatus.OPEN;
            }
            return order;
        }
    }

    public static final class Shipment {
        private final int id;
        private final int orderId;
        private final String sellerCountry;
        private final String buyerCountry;
        private final String itemId;
        private final int quantity;
        private final int unitPrice;
        private final long originPos;
        private final long destinationPos;
        private final long createdAt;
        private UUID courier;
        private ShipmentStatus status;

        public Shipment(
            int id,
            int orderId,
            String sellerCountry,
            String buyerCountry,
            String itemId,
            int quantity,
            int unitPrice,
            long originPos,
            long destinationPos,
            long createdAt
        ) {
            this.id = id;
            this.orderId = orderId;
            this.sellerCountry = sellerCountry;
            this.buyerCountry = buyerCountry;
            this.itemId = itemId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.originPos = originPos;
            this.destinationPos = destinationPos;
            this.createdAt = createdAt;
            this.status = ShipmentStatus.WAITING_LOGISTICS;
        }

        public int id() { return id; }
        public int orderId() { return orderId; }
        public String sellerCountry() { return sellerCountry; }
        public String buyerCountry() { return buyerCountry; }
        public String itemId() { return itemId; }
        public int quantity() { return quantity; }
        public int unitPrice() { return unitPrice; }
        public long originPos() { return originPos; }
        public long destinationPos() { return destinationPos; }
        public long createdAt() { return createdAt; }
        public UUID courier() { return courier; }
        public ShipmentStatus status() { return status; }

        public void assignCourier(UUID courier) {
            this.courier = courier;
            this.status = ShipmentStatus.IN_TRANSIT;
        }

        public void delivered() {
            this.status = ShipmentStatus.DELIVERED;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", id);
            tag.putInt("order", orderId);
            tag.putString("seller", sellerCountry);
            tag.putString("buyer", buyerCountry);
            tag.putString("item", itemId);
            tag.putInt("quantity", quantity);
            tag.putInt("unit_price", unitPrice);
            tag.putLong("origin_pos", originPos);
            tag.putLong("destination_pos", destinationPos);
            tag.putLong("created", createdAt);
            if (courier != null) {
                tag.putUUID("courier", courier);
            }
            tag.putString("status", status.name());
            return tag;
        }

        public static Shipment load(CompoundTag tag) {
            Shipment shipment = new Shipment(
                tag.getInt("id"),
                tag.getInt("order"),
                tag.getString("seller"),
                tag.getString("buyer"),
                tag.getString("item"),
                tag.getInt("quantity"),
                tag.getInt("unit_price"),
                tag.getLong("origin_pos"),
                tag.getLong("destination_pos"),
                tag.getLong("created")
            );
            if (tag.hasUUID("courier")) {
                shipment.courier = tag.getUUID("courier");
            }
            try {
                shipment.status = ShipmentStatus.valueOf(tag.getString("status"));
            } catch (IllegalArgumentException ignored) {
                shipment.status = shipment.courier == null
                    ? ShipmentStatus.WAITING_LOGISTICS
                    : ShipmentStatus.IN_TRANSIT;
            }
            return shipment;
        }
    }
}

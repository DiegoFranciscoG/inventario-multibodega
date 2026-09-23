package io.github.diegofranciscog.inventory.entity;

import java.lang.reflect.Field;

/** Construye entidades en memoria para tests unitarios (asigna ids como lo haría la base de datos). */
public final class DomainFixtures {

    private DomainFixtures() {
    }

    public static Warehouse warehouse(long id, String code) {
        return withId(new Warehouse(code, "Bodega " + code, "Quito", null), id);
    }

    public static Location location(long id, Warehouse warehouse) {
        return withId(new Location(warehouse, "01", "01", "1", LocationType.STORAGE), id);
    }

    public static Product product(long id, String sku, boolean lotControlled) {
        Category category = withId(new Category("CAT", "Categoría"), 1L);
        UnitOfMeasure unit = newInstance(UnitOfMeasure.class);
        setField(unit, "code", "H87");
        setField(unit, "name", "Pieza");
        return withId(new Product(sku, null, "Producto " + sku, null, category, unit, lotControlled, AbcClass.A), id);
    }

    public static AppUser user(long id, Role role) {
        return withId(new AppUser("user" + id + "@demo.local", "Usuario " + id, "{noop}x", role), id);
    }

    public static <T> T withId(T entity, long id) {
        setField(entity, "id", id);
        return entity;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}

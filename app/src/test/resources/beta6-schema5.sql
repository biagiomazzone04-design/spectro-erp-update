-- Original Beta 6 schema, commit 5d8132296ee50be195055f6028367fbaf127cc53
CREATE TABLE clients(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                default_print_price REAL NOT NULL DEFAULT 0
            );
CREATE TABLE inventory(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_type TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                fabric_name TEXT NOT NULL,
                quantity_m REAL NOT NULL DEFAULT 0,
                purchase_price_m REAL NOT NULL DEFAULT 0,
                created_date TEXT NOT NULL,
                images_json TEXT NOT NULL DEFAULT '[]',
                minimum_quantity_m REAL NOT NULL DEFAULT 0,
                supplier_name TEXT NOT NULL DEFAULT '',
                composition TEXT NOT NULL DEFAULT '',
                weight_gsm TEXT NOT NULL DEFAULT '',
                variant TEXT NOT NULL DEFAULT '',
                lot_code TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE production(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                date TEXT NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                order_ref TEXT NOT NULL DEFAULT '',
                designs_json TEXT NOT NULL DEFAULT '[]',
                print_price_m REAL NOT NULL DEFAULT 0,
                plotter TEXT NOT NULL DEFAULT 'X1',
                printed_paper_m REAL NOT NULL DEFAULT 0,
                protection_paper_m REAL NOT NULL DEFAULT 0,
                paper_waste_m REAL NOT NULL DEFAULT 0,
                fabric_mode TEXT NOT NULL DEFAULT 'NONE',
                fabric_id INTEGER,
                fabric_used_m REAL NOT NULL DEFAULT 0,
                fabric_waste_m REAL NOT NULL DEFAULT 0,
                fabric_sale_price_m REAL NOT NULL DEFAULT 0,
                manual_fabric_name TEXT NOT NULL DEFAULT '',
                fabric_display_name TEXT NOT NULL DEFAULT 'Nessuno',
                technical_notes TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                refund_cost REAL NOT NULL DEFAULT 0,
                fabric_loss_cost REAL NOT NULL DEFAULT 0,
                apply_fixed_costs INTEGER NOT NULL DEFAULT 1,
                paper_print_cost REAL NOT NULL DEFAULT 0,
                protection_paper_cost REAL NOT NULL DEFAULT 0,
                fabric_used_cost REAL NOT NULL DEFAULT 0,
                material_cost_total REAL NOT NULL DEFAULT 0,
                gross_revenue REAL NOT NULL DEFAULT 0,
                gross_profit REAL NOT NULL DEFAULT 0,
                work_order_id INTEGER
            );
CREATE TABLE work_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                received_date TEXT NOT NULL,
                client TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'IN_PROCESS',
                due_date TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                blocker TEXT NOT NULL DEFAULT 'NONE',
                client_id INTEGER,
                order_code TEXT NOT NULL DEFAULT '',
                fabric_inventory_id INTEGER,
                ordered_m REAL NOT NULL DEFAULT 0,
                print_price_m REAL NOT NULL DEFAULT 0,
                design_code TEXT NOT NULL DEFAULT '',
                production_record_id INTEGER,
                effective_delivery_date TEXT NOT NULL DEFAULT '',
                cancel_reason TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE settings(
                id INTEGER PRIMARY KEY CHECK (id = 1),
                daily_fixed_cost REAL NOT NULL,
                plotter_x1_cost REAL NOT NULL,
                plotter_x2_cost REAL NOT NULL,
                protection_cost REAL NOT NULL,
                theme_key TEXT NOT NULL,
                zentha_split_a REAL NOT NULL DEFAULT 50,
                zentha_split_b REAL NOT NULL DEFAULT 50
            );
CREATE TABLE IF NOT EXISTS client_issues(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                responsibility TEXT NOT NULL DEFAULT 'TO_VERIFY',
                severity TEXT NOT NULL DEFAULT 'MEDIUM',
                status TEXT NOT NULL DEFAULT 'OPEN',
                opened_date TEXT NOT NULL,
                expected_delivery_date TEXT NOT NULL DEFAULT '',
                revised_delivery_date TEXT NOT NULL DEFAULT '',
                delay_days INTEGER NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                internal_notes TEXT NOT NULL DEFAULT '',
                evidence_images_json TEXT NOT NULL DEFAULT '[]'
            );
CREATE TABLE IF NOT EXISTS issue_updates(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                issue_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                note TEXT NOT NULL,
                images_json TEXT NOT NULL DEFAULT '[]'
            );
CREATE TABLE IF NOT EXISTS payments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                amount REAL NOT NULL DEFAULT 0,
                due_date TEXT NOT NULL,
                paid INTEGER NOT NULL DEFAULT 0,
                paid_date TEXT NOT NULL DEFAULT '',
                method TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                notes TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE IF NOT EXISTS fabric_rolls(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_id INTEGER NOT NULL,
                roll_code TEXT NOT NULL,
                initial_m REAL NOT NULL DEFAULT 0,
                available_m REAL NOT NULL DEFAULT 0,
                created_date TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'INTACT',
                images_json TEXT NOT NULL DEFAULT '[]'
            );
CREATE TABLE IF NOT EXISTS stock_movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_id INTEGER NOT NULL,
                roll_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                date_time TEXT NOT NULL,
                meters REAL NOT NULL DEFAULT 0,
                order_kind TEXT,
                order_id INTEGER,
                order_ref TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE IF NOT EXISTS roll_usages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_kind TEXT NOT NULL,
                order_id INTEGER NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                inventory_id INTEGER NOT NULL,
                roll_id INTEGER NOT NULL,
                meters REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            );
CREATE TABLE IF NOT EXISTS order_timeline(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_order_id INTEGER NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL,
                event_date TEXT NOT NULL,
                event_time TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'NOTE',
                responsibility TEXT NOT NULL DEFAULT 'NONE',
                severity TEXT NOT NULL DEFAULT 'INFO',
                status TEXT NOT NULL DEFAULT 'OPEN',
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                old_due_date TEXT NOT NULL DEFAULT '',
                new_due_date TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                resolution_note TEXT NOT NULL DEFAULT '',
                resolved_date TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE IF NOT EXISTS cotton_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_code TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                fabric_name TEXT NOT NULL,
                print_type TEXT NOT NULL DEFAULT 'REACTIVE',
                ordered_m REAL NOT NULL DEFAULT 0,
                actual_produced_m REAL NOT NULL DEFAULT 0,
                delivered_m REAL NOT NULL DEFAULT 0,
                defective_m REAL NOT NULL DEFAULT 0,
                sample_m REAL NOT NULL DEFAULT 0,
                leftover_m REAL NOT NULL DEFAULT 0,
                production_cost_m REAL NOT NULL DEFAULT 0,
                print_price_m REAL NOT NULL DEFAULT 0,
                fabric_owner_type TEXT NOT NULL DEFAULT 'CLIENT',
                inventory_id INTEGER,
                fabric_cost_m REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'IN_PROCESS',
                title TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]'
            );
CREATE TABLE IF NOT EXISTS zentha_products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sku TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
                standard_cost REAL NOT NULL DEFAULT 0,
                standard_price REAL NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                image_path TEXT NOT NULL DEFAULT '',
                active INTEGER NOT NULL DEFAULT 1
            );
CREATE TABLE IF NOT EXISTS zentha_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_code TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'CONFIRMED',
                notes TEXT NOT NULL DEFAULT '',
                attachments_json TEXT NOT NULL DEFAULT '[]',
                split_a REAL NOT NULL DEFAULT 50,
                split_b REAL NOT NULL DEFAULT 50
            );
CREATE TABLE IF NOT EXISTS zentha_order_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER,
                product_name TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 0,
                unit_price REAL NOT NULL DEFAULT 0,
                unit_cost REAL NOT NULL DEFAULT 0
            );
CREATE TABLE IF NOT EXISTS zentha_extra_costs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0
            );
CREATE TABLE IF NOT EXISTS zentha_payments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                payment_date TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                method TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                notes TEXT NOT NULL DEFAULT ''
            );
CREATE TABLE IF NOT EXISTS economic_works(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_date TEXT NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                unit_price REAL NOT NULL DEFAULT 0,
                manual_gross REAL NOT NULL DEFAULT 0,
                materials_json TEXT NOT NULL DEFAULT '[]',
                other_costs REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'OPEN'
            );
CREATE TABLE IF NOT EXISTS operational_tasks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                work_order_id INTEGER,
                title TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'OTHER',
                description TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                status TEXT NOT NULL DEFAULT 'TODO',
                images_json TEXT NOT NULL DEFAULT '[]'
            );
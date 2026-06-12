-- 1. Таблица Гостей (Проживающих в отелях)
CREATE TABLE IF NOT EXISTS guests (
                                      id BIGSERIAL PRIMARY KEY,
                                      hotel_key VARCHAR(50) NOT NULL,
    chat_id VARCHAR(100) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    room_number VARCHAR(20),
    passport_data_encrypted TEXT,
    status VARCHAR(30) NOT NULL -- 'CHECKED_IN', 'CHECKED_OUT'
    );

-- Индекс для быстрого поиска гостя по его сессии из чата и ключу отеля (SaaS изоляция)
CREATE INDEX IF NOT EXISTS idx_guests_chat_hotel ON guests(chat_id, hotel_key);


-- 2. Таблица Персонала отеля (Горничные, Официанты)
CREATE TABLE IF NOT EXISTS hotel_staff (
                                           id BIGSERIAL PRIMARY KEY,
                                           hotel_key VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    role VARCHAR(50) NOT NULL,   -- 'MAID', 'WAITER', 'TECHNICIAN'
    status VARCHAR(30) NOT NULL  -- 'FREE', 'BUSY', 'OFF_DUTY'
    );


-- 3. Таблица Ресторанного Меню (Товары и Складской учет)
CREATE TABLE IF NOT EXISTS food_menu (
                                         id BIGSERIAL PRIMARY KEY,
                                         hotel_key VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0
    );


-- 4. Таблица Заявок и Заказов (Учет транзакций ИИ)
CREATE TABLE IF NOT EXISTS action_tickets (
                                              id BIGSERIAL PRIMARY KEY,
                                              hotel_key VARCHAR(50) NOT NULL,
    guest_id BIGINT NOT NULL,
    ticket_type VARCHAR(50) NOT NULL, -- 'ORDER_FOOD', 'ROOM_CLEANING'
    status VARCHAR(30) NOT NULL,      -- 'CREATED', 'IN_PROGRESS', 'QUEUED', 'COMPLETED', 'REJECTED'
    assigned_staff_id BIGINT,         -- Ссылка на сотрудника, может быть NULL
    total_price NUMERIC(10, 2),       -- Заполняется для заказов еды
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tickets_guest FOREIGN KEY (guest_id) REFERENCES guests(id) ON DELETE CASCADE,
    CONSTRAINT fk_tickets_staff FOREIGN KEY (assigned_staff_id) REFERENCES hotel_staff(id) ON DELETE SET NULL
    );
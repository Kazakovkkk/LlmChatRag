const jsonHeaders = { 'Content-Type': 'application/json' };

// Глобальный кэш документов для фронтенд-фильтрации
window.cachedChunks = [];

function switchTab(tabId) {
    // 1. Явно перечисляем ВСЕ ID вкладок (и ваши старые, и наши новые)
    const allTabIds = [
        'uploadTab',
        'knowledgeTab',
        'historyTab',
        'guestsTab',
        'staffTab',
        'menuTab',
        'ticketsTab'
    ];

    // Скрываем абсолютно все вкладки из списка по их ID
    allTabIds.forEach(id => {
        const tab = document.getElementById(id);
        if (tab) {
            tab.style.display = 'none';
        }
    });

    // Показываем только ту вкладку, на которую кликнули
    const targetTab = document.getElementById(tabId);
    if (targetTab) {
        targetTab.style.display = 'block';
    }

    // 2. Управляем подсветкой кнопок в сайдбаре (тут всё работает как надо)
    document.querySelectorAll('.sidebar .tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    // Находим активную кнопку по паттерну "btn-[имяВкладки]"
    const activeBtn = document.getElementById('btn-' + tabId);
    if (activeBtn) {
        activeBtn.classList.add('active');
    }

    // 3. Динамическая подгрузка данных при клике
    switch (tabId) {
        case 'historyTab':
            if (typeof loadHistory === 'function') loadHistory(); // Ваш старый метод аудита чатов
            break;
        case 'knowledgeTab':
            if (typeof loadChunks === 'function') loadChunks(); // Ваш старый метод чанков
            break;
        case 'guestsTab':
            loadGuestsData();
            break;
        case 'staffTab':
            loadStaffData();
            break;
        case 'menuTab':
            loadMenuData();
            break;
        case 'ticketsTab':
            loadTicketsData();
            break;
    }
}

// --- ЛОГИКА ДЛЯ ЗОНЫ DRAG & DROP ---
document.addEventListener("DOMContentLoaded", () => {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('pdfFile');

    if(dropZone && fileInput) {
        dropZone.addEventListener('click', () => fileInput.click());

        fileInput.addEventListener('change', (e) => handleFileSelect(e.target.files[0]));

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('drag-over');
        });

        ['dragleave', 'dragend'].forEach(type => {
            dropZone.addEventListener(type, () => dropZone.classList.remove('drag-over'));
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            if(e.dataTransfer.files.length > 0) {
                fileInput.files = e.dataTransfer.files;
                handleFileSelect(e.dataTransfer.files[0]);
            }
        });
    }
    loadChunks();
});

function handleFileSelect(file) {
    if (!file || file.type !== "application/pdf") {
        alert("Пожалуйста, выберите корректный PDF-файл");
        return;
    }
    document.getElementById('fileNameDisplay').textContent = file.name;
    document.getElementById('fileInfo').style.display = 'block';
}

// --- ОТПРАВКА С ВЫБОРОМ РЕЖИМА ---
async function uploadPdf() {
    const fileInput = document.getElementById('pdfFile');
    if(!fileInput.files[0]) {
        alert("Сначала перетащите или выберите PDF-файл!");
        return;
    }

    // Вытаскиваем выбранный режим обработки информации (APPEND или OVERWRITE)
    const selectedMode = document.querySelector('input[name="uploadMode"]:checked').value;

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('mode', selectedMode); // Подмешиваем режим в multipart запрос

    const res = await fetch('/admin/upload', {
        method: 'POST',
        body: formData
    });
    if(res.ok) {
        alert(`Файл успешно векторизован в режиме: ${selectedMode}!`);
        fileInput.value = '';
        document.getElementById('fileInfo').style.display = 'none';
        switchTab('knowledgeTab');
    } else {
        alert("Произошла ошибка при обработке файла.");
    }
}

async function loadChunks() {
    const container = document.getElementById('chunksContainer');
    const res = await fetch('/admin/knowledge-base');
    const chunks = await res.json();

    // Сохраняем в кэш
    window.cachedChunks = chunks;
    renderChunks(window.cachedChunks);
}

// Вынесли рендеринг в отдельную функцию, чтобы её вызывал поисковый фильтр
function renderChunks(chunksList) {
    const container = document.getElementById('chunksContainer');
    if(chunksList.length === 0) {
        container.innerHTML = '<div style="color: #8899aa; padding: 20px;">Совпадений не найдено</div>';
        return;
    }
    container.innerHTML = chunksList.map(c => `
        <div class="chunk-item">
            <div style="font-size:10px; color:#8899aa; margin-bottom:5px;">UUID точки: ${c.id}</div>
            <div class="chunk-text" id="text-${c.id}" contenteditable="false">${c.text}</div>
            <button class="btn" id="btn-${c.id}" onclick="toggleEdit('${c.id}')">Редактировать чанк</button>
        </div>
    `).join('');
}

// === НОВАЯ КЛИЕНТСКАЯ ФИЛЬТРАЦИЯ ЧАНКОВ (Мгновенная работа без дёргания Qdrant) ===
function filterChunks() {
    const query = document.getElementById('chunkSearchInput').value.toLowerCase().trim();
    if(!query) {
        renderChunks(window.cachedChunks); // Если строка пуста, выводим всё
        return;
    }
    const filtered = window.cachedChunks.filter(c => c.text && c.text.toLowerCase().includes(query));
    renderChunks(filtered);
}

function toggleEdit(id) {
    const textEl = document.getElementById(`text-${id}`);
    const btn = document.getElementById(`btn-${id}`);
    if (textEl.getAttribute('contenteditable') === 'false') {
        textEl.setAttribute('contenteditable', 'true');
        textEl.focus();
        btn.textContent = "Сохранить изменения";
        btn.style.background = "#4caf50";
    } else {
        saveChunk(id, textEl.textContent);
    }
}
async function loadGuestsData() {
    const response = await fetch('/admin/management/guests');
    const guests = await response.json();
    const tbody = document.getElementById('guests-table-body');
    tbody.innerHTML = '';

    guests.forEach(g => {
        tbody.innerHTML += `
            <tr>
                <td><strong>${g.firstName} ${g.lastName}</strong></td>
                <td>Номер ${g.roomNumber}</td>
                <td><code>${g.chatId}</code></td>
                <td><span class="badge status-${g.status.toLowerCase()}">${g.status}</span></td>
                <td>
                    <select onchange="updateGuestStatus('${g.id}', '${g.chatId}', '${g.firstName}', '${g.lastName}', '${g.roomNumber}', this.value)">
                        <option value="CHECKED_IN" ${g.status === 'CHECKED_IN' ? 'selected' : ''}>Заселен (CHECKED_IN)</option>
                        <option value="CHECKED_OUT" ${g.status === 'CHECKED_OUT' ? 'selected' : ''}>Выписан (CHECKED_OUT)</option>
                    </select>
                </td>
            </tr>
        `;
    });
}

async function updateGuestStatus(id, chatId, firstName, lastName, roomNumber, newStatus) {
    const payload = { id, chatId, firstName, lastName, roomNumber, status: newStatus };
    const response = await fetch('/admin/management/guests', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (response.ok) alert("Статус гостя успешно изменен в PMS");
}

// ===================================================================
// 2. ЛОГИКА ВКЛАДКИ "ПЕРСОНАЛ"
// ===================================================================
async function loadStaffData() {
    const response = await fetch('/admin/management/staff');
    const staff = await response.json();
    const tbody = document.getElementById('staff-table-body');
    tbody.innerHTML = '';

    staff.forEach(s => {
        tbody.innerHTML += `
            <tr>
                <td>${s.name}</td>
                <td><span class="role-tag">${s.role}</span></td>
                <td><span class="badge staff-${s.status.toLowerCase()}">${s.status}</span></td>
                <td>
                    <select onchange="updateStaffStatus('${s.id}', '${s.name}', '${s.role}', this.value)">
                        <option value="FREE" ${s.status === 'FREE' ? 'selected' : ''}>Свободен (FREE)</option>
                        <option value="BUSY" ${s.status === 'BUSY' ? 'selected' : ''}>В работе (BUSY)</option>
                        <option value="OFF_DUTY" ${s.status === 'OFF_DUTY' ? 'selected' : ''}>Не на смене (OFF_DUTY)</option>
                    </select>
                </td>
            </tr>
        `;
    });
}

async function updateStaffStatus(id, name, role, newStatus) {
    const payload = { id, name, role, status: newStatus };
    const response = await fetch('/admin/management/staff', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (response.ok) loadStaffData(); // Перерисовываем таблицу для обновления цветов
}

// ===================================================================
// 3. ЛОГИКА ВКЛАДКИ "МЕНЮ И СКЛАД"
// ===================================================================
async function loadMenuData() {
    const response = await fetch('/admin/management/menu');
    const menu = await response.json();
    const tbody = document.getElementById('menu-table-body');
    tbody.innerHTML = '';

    menu.forEach(item => {
        tbody.innerHTML += `
            <tr>
                <td><strong>${item.name}</strong></td>
                <td>${item.price} руб.</td>
                <td>
                    <input type="number" id="stock-input-${item.id}" value="${item.stockQuantity}" min="0" style="width: 70px;">
                </td>
                <td>
                    <button class="save-btn" onclick="updateStock('${item.id}')">💾 Обновить склад</button>
                </td>
            </tr>
        `;
    });
}

async function updateStock(itemId) {
    const input = document.getElementById(`stock-input-${itemId}`);
    const count = parseInt(input.value);

    const response = await fetch(`/admin/management/menu/stock/${itemId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ stockQuantity: count })
    });
    if (response.ok) alert("Складские остатки обновлены в PostgreSQL");
}

// ===================================================================
// 4. ЛОГИКА ВКЛАДКИ "ЖУРНАЛ ЗАЯВОК ИИ"
// ===================================================================
async function loadTicketsData() {
    const response = await fetch('/admin/management/tickets');
    const tickets = await response.json();
    const tbody = document.getElementById('tickets-table-body');
    tbody.innerHTML = '';

    tickets.forEach(t => {
        // Форматируем вывод цены
        const priceDisplay = t.totalPrice > 0 ? `${t.totalPrice} руб.` : '—';

        tbody.innerHTML += `
            <tr>
                <td><small>${t.createdAt.replace('T', ' ')}</small></td>
                <td>Комн. ${t.roomNumber} (${t.guestName})</td>
                <td><span class="ticket-type">${t.ticketType}</span></td>
                <td><strong>${priceDisplay}</strong></td>
                <td><small>${t.assignedStaffName}</small></td>
                <td><span class="badge ticket-${t.status.toLowerCase()}">${t.status}</span></td>
                <td>
                    <select onchange="updateTicketStatus('${t.id}', this.value)" ${t.status === 'COMPLETED' || t.status === 'REJECTED' ? 'disabled' : ''}>
                        <option value="CREATED" ${t.status === 'CREATED' ? 'selected' : ''}>Создан</option>
                        <option value="IN_PROGRESS" ${t.status === 'IN_PROGRESS' ? 'selected' : ''}>В работе</option>
                        <option value="COMPLETED" ${t.status === 'COMPLETED' ? 'selected' : ''}>Выполнен</option>
                        <option value="REJECTED" ${t.status === 'REJECTED' ? 'selected' : ''}>Отменен</option>
                    </select>
                </td>
            </tr>
        `;
    });
}

async function updateTicketStatus(ticketId, newStatus) {
    const response = await fetch(`/admin/management/tickets/${ticketId}/status`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus })
    });
    if (response.ok) {
        // Перезагружаем вкладку, так как при закрытии тикета бэкенд мог автоматически освободить горничную!
        loadTicketsData();
    }
}

async function saveChunk(id, newText) {
    const res = await fetch(`/admin/knowledge-base/chunk/${id}`, {
        method: 'PUT',
        headers: jsonHeaders,
        body: JSON.stringify({ text: newText })
    });
    if(res.ok) {
        alert("Вектор успешно пересчитан в Qdrant!");
        loadChunks();
    }
}

async function loadHistory() {
    const container = document.getElementById('chatsContainer');
    const res = await fetch('/admin/chats/history');
    const chats = await res.json();
    container.innerHTML = chats.map(c => `
        <div class="chat-session">
            <h4 style="margin:0 0 10px 0; color:#8899aa;">Сессия гостя: ${c.id}</h4>
            ${c.messages.map(m => `
                <div class="msg ${m.role}">
                    <b>${m.role === 'user' ? 'Гость' : 'ИИ-Ассистент'}:</b>
                    <span id="msg-text-${m.id}">${m.content}</span>
                    ${m.role === 'assistant' ? `<a href="#" style="color:#e94560; font-size:11px; margin-left:10px;" onclick="correctMessage(${m.id})">[Исправить ответ ИИ]</a>` : ''}
                </div>
            `).join('')}
        </div>
    `).join('');
}

function correctMessage(id) {
    const txt = prompt("Введите правильный ответ для гостя:");
    if(!txt) return;
    fetch(`/admin/chats/message/${id}`, {
        method: 'PUT',
        headers: jsonHeaders,
        body: JSON.stringify({ text: txt })
    }).then(() => loadHistory());
}
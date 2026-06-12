const jsonHeaders = { 'Content-Type': 'application/json' };

window.cachedChunks = [];

function switchTab(tabId) {
    const allTabIds = ['uploadTab', 'knowledgeTab', 'historyTab', 'guestsTab', 'staffTab', 'menuTab', 'ticketsTab'];
    allTabIds.forEach(id => {
        const tab = document.getElementById(id);
        if (tab) tab.style.display = 'none';
    });

    const targetTab = document.getElementById(tabId);
    if (targetTab) targetTab.style.display = 'block';

    document.querySelectorAll('.sidebar .tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });

    const activeBtn = document.getElementById('btn-' + tabId);
    if (activeBtn) activeBtn.classList.add('active');

    switch (tabId) {
        case 'historyTab': loadHistory(); break;
        case 'knowledgeTab': loadChunks(); break;
        case 'guestsTab': loadGuestsData(); break;
        case 'staffTab': loadStaffData(); break;
        case 'menuTab': loadMenuData(); break;
        case 'ticketsTab': loadTicketsData(); break;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const dropZone = document.getElementById('dropZone');
    const fileInput = document.getElementById('pdfFile');

    if(dropZone && fileInput) {
        dropZone.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', (e) => handleFileSelect(e.target.files[0]));
        dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
        ['dragleave', 'dragend'].forEach(type => { dropZone.addEventListener(type, () => dropZone.classList.remove('drag-over')); });
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            if(e.dataTransfer.files.length > 0) { fileInput.files = e.dataTransfer.files; handleFileSelect(e.dataTransfer.files[0]); }
        });
    }
    loadChunks();
});

function handleFileSelect(file) {
    if (!file || file.type !== "application/pdf") { alert("Пожалуйста, выберите корректный PDF-файл"); return; }
    document.getElementById('fileNameDisplay').textContent = file.name;
    document.getElementById('fileInfo').style.display = 'block';
}

async function uploadPdf() {
    const fileInput = document.getElementById('pdfFile');
    if(!fileInput.files[0]) { alert("Сначала перетащите или выберите PDF-файл!"); return; }

    const selectedMode = document.querySelector('input[name="uploadMode"]:checked').value;
    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('mode', selectedMode);

    const res = await fetch('/admin/upload', { method: 'POST', body: formData });
    if(res.ok) {
        alert(`Файл успешно векторизован в режиме: ${selectedMode}!`);
        fileInput.value = '';
        document.getElementById('fileInfo').style.display = 'none';
        switchTab('knowledgeTab');
    } else {
        alert("Произошла ошибка при обработке файла.");
    }
}

// --- УПРАВЛЕНИЕ МОДАЛЬНЫМ ОКНОМ ---
function openCrudModal(title, formHtml, saveAction) {
    document.getElementById('modalTitle').textContent = title;
    document.getElementById('modalFormBody').innerHTML = formHtml;
    document.getElementById('crudModal').style.display = 'flex';
    document.getElementById('modalSaveBtn').onclick = saveAction;
}

function closeCrudModal() {
    document.getElementById('crudModal').style.display = 'none';
}

// --- ЛОГИКА ВКЛАДКИ: ГОСТИ ---
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
                <td><span class="badge">${g.status}</span></td>
                <td>
                    <button class="btn-secondary" onclick="openEditGuestModal('${g.id}', '${g.chatId}', '${g.firstName}', '${g.lastName}', '${g.roomNumber}', '${g.passportDataEncrypted}', '${g.status}')">Изменить</button>
                </td>
            </tr>
        `;
    });
}

function openAddGuestModal() {
    const formHtml = `
        <div class="form-group"><label>Имя</label><input type="text" id="g-firstName"></div>
        <div class="form-group"><label>Фамилия</label><input type="text" id="g-lastName"></div>
        <div class="form-group"><label>Номер комнаты</label><input type="text" id="g-roomNumber"></div>
        <div class="form-group"><label>ID Сессии чата</label><input type="text" id="g-chatId"></div>
        <div class="form-group"><label>Данные паспорта</label><input type="text" id="g-passport"></div>
        <div class="form-group"><label>Статус</label><select id="g-status"><option value="CHECKED_IN">Заселен</option><option value="CHECKED_OUT">Выписан</option></select></div>
    `;
    openCrudModal("Добавить гостя", formHtml, async () => {
        const payload = {
            id: "0", chatId: document.getElementById('g-chatId').value, firstName: document.getElementById('g-firstName').value,
            lastName: document.getElementById('g-lastName').value, roomNumber: document.getElementById('g-roomNumber').value,
            passportDataEncrypted: document.getElementById('g-passport').value, status: document.getElementById('g-status').value
        };
        const res = await fetch('/admin/management/guests', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadGuestsData(); }
    });
}

function openEditGuestModal(id, chatId, firstName, lastName, roomNumber, passport, status) {
    const formHtml = `
        <div class="form-group"><label>Имя</label><input type="text" id="g-firstName" value="${firstName}"></div>
        <div class="form-group"><label>Фамилия</label><input type="text" id="g-lastName" value="${lastName}"></div>
        <div class="form-group"><label>Номер комнаты</label><input type="text" id="g-roomNumber" value="${roomNumber}"></div>
        <div class="form-group"><label>ID Сессии чата (Блокировано)</label><input type="text" id="g-chatId" value="${chatId}" disabled></div>
        <div class="form-group"><label>Данные паспорта</label><input type="text" id="g-passport" value="${passport}"></div>
        <div class="form-group"><label>Статус</label><select id="g-status"><option value="CHECKED_IN" ${status==='CHECKED_IN'?'selected':''}>Заселен</option><option value="CHECKED_OUT" ${status==='CHECKED_OUT'?'selected':''}>Выписан</option></select></div>
    `;
    openCrudModal("Изменить гостя", formHtml, async () => {
        const payload = {
            id, chatId, firstName: document.getElementById('g-firstName').value, lastName: document.getElementById('g-lastName').value,
            roomNumber: document.getElementById('g-roomNumber').value, passportDataEncrypted: document.getElementById('g-passport').value,
            status: document.getElementById('g-status').value
        };
        const res = await fetch('/admin/management/guests', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadGuestsData(); }
    });
}

// --- ЛОГИКА ВКЛАДКИ: ПЕРСОНАЛ ---
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
                <td><span class="badge">${s.status}</span></td>
                <td>
                    <button class="btn-secondary" onclick="openEditStaffModal('${s.id}', '${s.name}', '${s.role}', '${s.status}')">Изменить</button>
                    <button class="btn-danger" onclick="deleteStaff('${s.id}')">Удалить</button>
                </td>
            </tr>
        `;
    });
}

function openAddStaffModal() {
    const formHtml = `
        <div class="form-group"><label>Имя сотрудника</label><input type="text" id="s-name"></div>
        <div class="form-group"><label>Роль</label><select id="s-role"><option value="MAID">Горничная (MAID)</option><option value="WAITER">Официант (WAITER)</option><option value="TECHNICIAN">Техник (TECHNICIAN)</option></select></div>
        <div class="form-group"><label>Статус</label><select id="s-status"><option value="FREE">Свободен</option><option value="BUSY">В работе</option><option value="OFF_DUTY">Не на смене</option></select></div>
    `;
    openCrudModal("Добавить сотрудника", formHtml, async () => {
        const payload = { id: "0", name: document.getElementById('s-name').value, role: document.getElementById('s-role').value, status: document.getElementById('s-status').value };
        const res = await fetch('/admin/management/staff', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadStaffData(); }
    });
}

function openEditStaffModal(id, name, role, status) {
    const formHtml = `
        <div class="form-group"><label>Имя сотрудника</label><input type="text" id="s-name" value="${name}"></div>
        <div class="form-group"><label>Роль</label><select id="s-role"><option value="MAID" ${role==='MAID'?'selected':''}>Горничная</option><option value="WAITER" ${role==='WAITER'?'selected':''}>Официант</option><option value="TECHNICIAN" ${role==='TECHNICIAN'?'selected':''}>Техник</option></select></div>
        <div class="form-group"><label>Статус</label><select id="s-status"><option value="FREE" ${status==='FREE'?'selected':''}>Свободен</option><option value="BUSY" ${status==='BUSY'?'selected':''}>В работе</option><option value="OFF_DUTY" ${status==='OFF_DUTY'?'selected':''}>Не на смене</option></select></div>
    `;
    openCrudModal("Изменить характеристики", formHtml, async () => {
        const payload = { id, name: document.getElementById('s-name').value, role: document.getElementById('s-role').value, status: document.getElementById('s-status').value };
        const res = await fetch('/admin/management/staff', { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadStaffData(); }
    });
}

async function deleteStaff(id) {
    if(confirm("Удалить сотрудника из штата?")) {
        const res = await fetch(`/admin/management/staff/${id}`, { method: 'DELETE' });
        if(res.ok) loadStaffData();
    }
}

// --- ЛОГИКА ВКЛАДКИ: СКЛАД И МЕНЮ ---
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
                <td>${item.stockQuantity} шт.</td>
                <td>
                    <button class="btn-secondary" onclick="openEditMenuModal('${item.id}', '${item.name}', ${item.price}, ${item.stockQuantity})">Изменить</button>
                    <button class="btn-danger" onclick="deleteMenuItem('${item.id}')">Удалить</button>
                </td>
            </tr>
        `;
    });
}

function openAddMenuModal() {
    const formHtml = `
        <div class="form-group"><label>Название блюда/напитка</label><input type="text" id="m-name"></div>
        <div class="form-group"><label>Цена (руб)</label><input type="number" id="m-price" min="0" step="0.01"></div>
        <div class="form-group"><label>Количество на складе</label><input type="number" id="m-stock" min="0"></div>
    `;
    openCrudModal("Добавить позицию номенклатуры", formHtml, async () => {
        const payload = { id: "0", name: document.getElementById('m-name').value, price: parseFloat(document.getElementById('m-price').value), stockQuantity: parseInt(document.getElementById('m-stock').value) };
        const res = await fetch('/admin/management/menu', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadMenuData(); }
    });
}

function openEditMenuModal(id, name, price, stock) {
    const formHtml = `
        <div class="form-group"><label>Название блюда</label><input type="text" id="m-name" value="${name}"></div>
        <div class="form-group"><label>Цена (Защищено/Изменение заблокировано в gRPC)</label><input type="number" id="m-price" value="${price}" disabled></div>
        <div class="form-group"><label>Остаток на складе</label><input type="number" id="m-stock" value="${stock}" min="0"></div>
    `;
    openCrudModal("Изменить характеристики склада", formHtml, async () => {
        const stockCount = parseInt(document.getElementById('m-stock').value);
        const res = await fetch(`/admin/management/menu/stock/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ stockQuantity: stockCount }) });
        if (res.ok) { closeCrudModal(); loadMenuData(); }
    });
}

async function deleteMenuItem(id) {
    if(confirm("Удалить позицию из каталога меню отеля?")) {
        const res = await fetch(`/admin/management/menu/${id}`, { method: 'DELETE' });
        if(res.ok) loadMenuData();
    }
}

// --- ЛОГИКА ВКЛАДКИ: ЖУРНАЛ ЗАЯВОК ИИ ---
async function loadTicketsData() {
    const response = await fetch('/admin/management/tickets');
    const tickets = await response.json();
    const tbody = document.getElementById('tickets-table-body');
    tbody.innerHTML = '';

    tickets.forEach(t => {
        const priceDisplay = t.totalPrice > 0 ? `${t.totalPrice} руб.` : '—';
        const formattedDate = t.createdAt ? t.createdAt.replace('T', ' ').substring(0, 16) : '—';

        tbody.innerHTML += `
            <tr>
                <td><small>${formattedDate}</small></td>
                <td>Номер ${t.roomNumber}</td> <td>${t.guestName}</td>        <td><span class="ticket-type">${t.ticketType}</span></td>
                <td><strong>${priceDisplay}</strong></td>
                <td><small>${t.assignedStaffName}</small></td>
                <td><span class="badge">${t.status}</span></td>
                <td>
                    <button class="btn-secondary" onclick="openEditTicketModal('${t.id}', '${t.status}')">Статус</button>
                    <button class="btn-danger" onclick="deleteTicket('${t.id}')">Удалить</button>
                </td>
            </tr>
        `;
    });
}

function openAddTicketModal() {
    const formHtml = `
        <div class="form-group"><label>ФИО гостя</label><input type="text" id="t-guestName"></div>
        <div class="form-group"><label>Комната</label><input type="text" id="t-room"></div>
        <div class="form-group"><label>Тип заявки</label><select id="t-type"><option value="ROOM_CLEANING">Уборка (ROOM_CLEANING)</option><option value="ORDER_FOOD">Заказ еды (ORDER_FOOD)</option></select></div>
        <div class="form-group"><label>Цена (руб)</label><input type="number" id="t-price" value="0" min="0"></div>
        <div class="form-group"><label>Статус</label><select id="t-status"><option value="CREATED">CREATED</option><option value="IN_PROGRESS">IN_PROGRESS</option></select></div>
    `;
    openCrudModal("Сформировать ручную заявку", formHtml, async () => {
        const payload = { guestName: document.getElementById('t-guestName').value, roomNumber: document.getElementById('t-room').value, ticketType: document.getElementById('t-type').value, totalPrice: parseFloat(document.getElementById('t-price').value), status: document.getElementById('t-status').value };
        const res = await fetch('/admin/management/tickets', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) });
        if (res.ok) { closeCrudModal(); loadTicketsData(); }
    });
}

function openEditTicketModal(id, currentStatus) {
    const formHtml = `
        <div class="form-group"><label>Текущий статус заявки</label>
            <select id="t-status">
                <option value="CREATED" ${currentStatus==='CREATED'?'selected':''}>Создан (CREATED)</option>
                <option value="IN_PROGRESS" ${currentStatus==='IN_PROGRESS'?'selected':''}>В работе (IN_PROGRESS)</option>
                <option value="COMPLETED" ${currentStatus==='COMPLETED'?'selected':''}>Выполнен (COMPLETED)</option>
                <option value="REJECTED" ${currentStatus==='REJECTED'?'selected':''}>Отменен (REJECTED)</option>
            </select>
        </div>
    `;
    openCrudModal("Изменить статус заявки", formHtml, async () => {
        const newStatus = document.getElementById('t-status').value;
        const res = await fetch(`/admin/management/tickets/${id}/status`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ status: newStatus }) });
        if (res.ok) { closeCrudModal(); loadTicketsData(); }
    });
}

async function deleteTicket(id) {
    if(confirm("Удалить архивную карточку заявки из логов PostgreSQL?")) {
        const res = await fetch(`/admin/management/tickets/${id}`, { method: 'DELETE' });
        if(res.ok) loadTicketsData();
    }
}

async function loadChunks() {
    const res = await fetch('/admin/knowledge-base');
    const chunks = await res.json();
    window.cachedChunks = chunks;
    renderChunks(window.cachedChunks);
}

function renderChunks(chunksList) {
    const container = document.getElementById('chunksContainer');
    if(chunksList.length === 0) { container.innerHTML = '<div style="color: #888888; padding: 20px;">Совпадений не найдено</div>'; return; }
    container.innerHTML = chunksList.map(c => `
        <div class="chunk-item">
            <div style="font-size:10px; color:#888888; margin-bottom:5px;">UUID точки: ${c.id}</div>
            <div class="chunk-text" id="text-${c.id}" contenteditable="false">${c.text}</div>
            <button class="btn" id="btn-${c.id}" onclick="toggleEdit('${c.id}')">Редактировать чанк</button>
        </div>
    `).join('');
}

function filterChunks() {
    const query = document.getElementById('chunkSearchInput').value.toLowerCase().trim();
    if(!query) { renderChunks(window.cachedChunks); return; }
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
        btn.style.background = "#ffffff";
        btn.style.color = "#121212";
    } else {
        saveChunk(id, textEl.textContent);
    }
}

async function saveChunk(id, newText) {
    const res = await fetch(`/admin/knowledge-base/chunk/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ text: newText }) });
    if(res.ok) { alert("Вектор успешно пересчитан в Qdrant!"); loadChunks(); }
}

async function loadHistory() {
    const container = document.getElementById('chatsContainer');
    const res = await fetch('/admin/chats/history');
    const chats = await res.json();
    container.innerHTML = chats.map(c => `
        <div class="chat-session">
            <h4 style="margin:0 0 10px 0; color:#888888;">Сессия гостя: ${c.id}</h4>
            ${c.messages.map(m => `
                <div class="msg ${m.role}">
                    <b>${m.role === 'user' ? 'Гость' : 'ИИ-Ассистент'}:</b>
                    <span id="msg-text-${m.id}">${m.content}</span>
                    ${m.role === 'assistant' ? `<a href="#" style="color:#ffffff; font-size:11px; margin-left:10px; text-decoration: underline;" onclick="correctMessage(${m.id})">[Исправить ответ ИИ]</a>` : ''}
                </div>
            `).join('')}
        </div>
    `).join('');
}

function correctMessage(id) {
    const txt = prompt("Введите правильный ответ для гостя:");
    if(!txt) return;
    fetch(`/admin/chats/message/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ text: txt }) }).then(() => loadHistory());
}
const jsonHeaders = { 'Content-Type': 'application/json' };

// Глобальный кэш документов для фронтенд-фильтрации
window.cachedChunks = [];

function switchTab(tab) {
    document.querySelectorAll('.tab-section').forEach(s => s.style.display = 'none');
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));

    document.getElementById(tab).style.display = 'block';
    document.getElementById('btn-' + tab).classList.add('active');

    if(tab === 'knowledgeTab') loadChunks();
    if(tab === 'historyTab') loadHistory();
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
const host = document.location.host

const feedSocket = new WebSocket(`ws://${host}/feed`)
const promptSocket = new WebSocket(`ws://${host}/prompt`)
const lsUsername = localStorage.getItem("username")
const username = lsUsername != null ? lsUsername : getUsername();

function getUsername() {
    const un = prompt("Please enter your username");
    localStorage.setItem("username", un);
    return un;
}

feedSocket.onmessage = (message) => {
    const resp = JSON.parse(message.data)
    document.getElementsByTagName('button')[0].removeAttribute('disabled')
    if(resp.images != null && resp.images.length > 0)
        handleSuccess(resp);
    else
        handleError();
}
function handleError() {
    alert('Error processing your request');
}

function handleSuccess(message) {
    const div = document.createElement("div")
    const id = new Date().getMilliseconds().toString() + "-"+message.prompt.replaceAll(" ","_")
    div.setAttribute("id", id)
    div.classList.add("feed-entry")
    document.getElementById("feed").prepend(div)
    const h3 = document.createElement("h3")
    h3.textContent = `"${message.prompt}" - ${message.username} at ${message.ts}`
    div.appendChild(h3);
    message.images.forEach(imageString => {
        const img = document.createElement("img")
        img.setAttribute("src", "data:image/png;base64," + imageString)
        div.appendChild(img)
    });
}

function submitPrompt() {
    const prompt = document.getElementById("prompt").value

    promptSocket.send(JSON.stringify({prompt: prompt, username: username}));
    document.getElementsByTagName('button')[0].setAttribute('disabled','true')
}

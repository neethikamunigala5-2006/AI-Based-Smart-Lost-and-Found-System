import { useCallback, useEffect, useRef, useState } from 'react'
import './App.css'

const BACKEND_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')
const API_URL = `${BACKEND_URL}/api/items`
const AUTH_API_URL = `${BACKEND_URL}/api/auth`
const TOKEN_STORAGE_KEY = 'campusFindToken'
const USER_STORAGE_KEY = 'campusFindUser'
const SESSION_EXPIRED_EVENT = 'campusFind:session-expired'
const DATA_REFRESH_INTERVAL_MS = 5000
const EMPTY_FILTERS = { category: '', location: '', type: '', status: '' }
const EMPTY_FORM = {
  title: '',
  description: '',
  category: '',
  color: '',
  brand: '',
  location: '',
  date: '',
  contactName: '',
  contactPhone: '',
  imageUrl: '',
}

async function request(url, options = {}) {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const isFormData = options.body instanceof FormData
  const response = await fetch(url, {
    ...options,
    headers: {
      ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      message = body.message || message
    } catch {
      // Keep the HTTP status message when the server response is not JSON.
    }
    if (response.status === 401 && !url.startsWith(AUTH_API_URL)) {
      const tokenIsStillCurrent = localStorage.getItem(TOKEN_STORAGE_KEY) === token
      if (tokenIsStillCurrent) {
        localStorage.removeItem(TOKEN_STORAGE_KEY)
        localStorage.removeItem(USER_STORAGE_KEY)
        window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT))
        message = 'Your session is no longer valid. Please log in again.'
      } else {
        message = 'The account changed while this request was in progress. Please retry.'
      }
    }
    const requestError = new Error(message)
    requestError.status = response.status
    throw requestError
  }

  if (response.status === 204) {
    return null
  }
  return response.json()
}

function readStoredUser() {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const storedUser = localStorage.getItem(USER_STORAGE_KEY)
  if (!token || !storedUser) return null

  try {
    return JSON.parse(storedUser)
  } catch {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
    return null
  }
}

function formatDate(date) {
  if (!date) return 'Date not specified'
  return new Date(`${date}T00:00:00`).toLocaleDateString()
}

function formatDateTime(dateTime) {
  if (!dateTime) return 'Date not available'
  const parsedDate = new Date(dateTime)
  return Number.isNaN(parsedDate.getTime()) ? 'Date not available' : parsedDate.toLocaleString()
}

function formatTimeAgo(dateTime) {
  if (!dateTime) return 'Date not available'
  const timestamp = new Date(dateTime).getTime()
  if (Number.isNaN(timestamp)) return 'Date not available'

  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000))
  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'} ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} ${hours === 1 ? 'hour' : 'hours'} ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} ${days === 1 ? 'day' : 'days'} ago`
  return new Date(timestamp).toLocaleString()
}

function itemMatchesSearchQuery(item, query) {
  const normalizedQuery = query.trim().toLocaleLowerCase()
  if (!normalizedQuery) return true

  return [
    item.title,
    item.description,
    item.category,
    item.brand,
    item.color,
    item.location,
  ].filter(Boolean).join(' ').toLocaleLowerCase().includes(normalizedQuery)
}

function formatClaimError(error) {
  if (error.status === 401) return 'Your session is not authorized. Please log in again.'
  if (error.status === 403) return 'Admin privileges are required to manage claims.'
  if (error.status === 404) return 'The claim could not be found. Refresh the claim list and try again.'
  if (error.status === 400) return `The claim could not be processed: ${error.message}`
  return `Could not process the claim: ${error.message}`
}

function NotificationBell({ userId, forceOpen = false }) {
  const [isOpen, setIsOpen] = useState(false)
  const [notifications, setNotifications] = useState([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const panelIsOpen = forceOpen || isOpen
  const mountedRef = useRef(true)
  const unreadRequestInFlightRef = useRef(false)
  const notificationsRequestInFlightRef = useRef(false)

  const loadUnreadNotifications = useCallback(async () => {
    if (unreadRequestInFlightRef.current || !mountedRef.current) return
    unreadRequestInFlightRef.current = true
    try {
      const unread = await request(`${BACKEND_URL}/api/notifications/user/${userId}/unread`)
      if (mountedRef.current) setUnreadCount(unread.length)
    } catch {
      if (mountedRef.current) setError('Unable to load notifications.')
    } finally {
      unreadRequestInFlightRef.current = false
    }
  }, [userId])

  const loadNotifications = useCallback(async (showLoading = false) => {
    if (notificationsRequestInFlightRef.current || !mountedRef.current) return
    notificationsRequestInFlightRef.current = true
    if (showLoading) setLoading(true)
    setError('')
    try {
      const allNotifications = await request(`${BACKEND_URL}/api/notifications/user/${userId}`)
      if (!mountedRef.current) return
      setNotifications(allNotifications)
    } catch {
      if (mountedRef.current) setError('Unable to load notifications.')
    } finally {
      notificationsRequestInFlightRef.current = false
      if (mountedRef.current && showLoading) setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    mountedRef.current = true
    const refresh = () => {
      if (document.visibilityState !== 'visible') return
      loadUnreadNotifications()
      if (panelIsOpen) loadNotifications()
    }
    refresh()
    const intervalId = window.setInterval(refresh, DATA_REFRESH_INTERVAL_MS)
    document.addEventListener('visibilitychange', refresh)

    return () => {
      mountedRef.current = false
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [panelIsOpen, loadNotifications, loadUnreadNotifications])

  async function togglePanel() {
    const opening = !isOpen
    setIsOpen(opening)
    if (opening) {
      await Promise.all([loadNotifications(true), loadUnreadNotifications()])
    }
  }

  async function markNotificationAsRead(notification) {
    if (notification.read) return
    setError('')
    try {
      await request(`${BACKEND_URL}/api/notifications/${notification.id}/read`, {
        method: 'PUT',
      })
      if (!mountedRef.current) return
      setNotifications((current) => current.map((entry) =>
        entry.id === notification.id ? { ...entry, read: true } : entry
      ))
      setUnreadCount((current) => Math.max(0, current - 1))
    } catch {
      if (mountedRef.current) setError('Unable to update this notification.')
    }
  }

  async function markAllAsRead() {
    if (unreadCount === 0) return
    setError('')
    try {
      await request(`${BACKEND_URL}/api/notifications/user/${userId}/read-all`, {
        method: 'PUT',
      })
      if (!mountedRef.current) return
      setNotifications((current) => current.map((notification) => ({ ...notification, read: true })))
      setUnreadCount(0)
    } catch {
      if (mountedRef.current) setError('Unable to update notifications.')
    }
  }

  return (
    <div className={forceOpen ? 'notification-page' : 'notification-menu'}>
      {!forceOpen && (
        <button
          className="notification-bell"
          type="button"
          aria-label={unreadCount > 0 ? `Notifications, ${unreadCount} unread` : 'Notifications'}
          aria-expanded={isOpen}
          onClick={togglePanel}
        >
          <span aria-hidden="true">🔔</span>
          {unreadCount > 0 && <span className="notification-count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
        </button>
      )}
      {panelIsOpen && (
        <section className={`notification-panel${forceOpen ? ' notification-page-panel' : ''}`} aria-label="Notifications">
          <div className="notification-panel-heading">
            <h2>Notifications</h2>
            <button
              className="notification-mark-all"
              type="button"
              onClick={markAllAsRead}
              disabled={unreadCount === 0 || loading}
            >
              Mark all as read
            </button>
          </div>
          {error && <p className="notification-error" role="alert">{error}</p>}
          {loading ? (
            <p className="notification-empty" role="status">Loading notifications…</p>
          ) : notifications.length === 0 ? (
            !error && <p className="notification-empty">No notifications yet.</p>
          ) : (
            <div className="notification-list">
              {notifications.map((notification) => (
                <button
                  className={`notification-entry ${notification.read ? 'read' : 'unread'}`}
                  key={notification.id}
                  type="button"
                  onClick={() => markNotificationAsRead(notification)}
                  aria-label={`${notification.read ? 'Read' : 'Unread'} notification: ${notification.message}`}
                >
                  <span className="notification-entry-marker" aria-hidden="true">
                    {notification.read ? '○' : '●'}
                  </span>
                  <span className="notification-entry-content">
                    <span className="notification-message">{notification.message}</span>
                    <span className="notification-meta">
                      {notification.type} · {formatTimeAgo(notification.createdAt)}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  )
}

function ReportForm({ type, onSubmit, busy }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [selectedImage, setSelectedImage] = useState(null)
  const [imageMessage, setImageMessage] = useState('')
  const [imageMessageType, setImageMessageType] = useState('success')
  const [previewUrl, setPreviewUrl] = useState('')
  const imageInputRef = useRef(null)

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function handleImageChange(event) {
    const file = event.target.files?.[0]
    if (!file) return

    const allowedMimeTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])
    const allowedExtension = /\.(jpe?g|png|webp)$/i.test(file.name)
    const hasInvalidMimeType = file.type !== '' && !allowedMimeTypes.has(file.type)

    if (hasInvalidMimeType || !allowedExtension) {
      setSelectedImage(null)
      setPreviewUrl('')
      setImageMessage('Please select a JPG, JPEG, PNG, or WEBP image.')
      setImageMessageType('error')
      event.target.value = ''
      return
    }

    if (file.size > 5 * 1024 * 1024) {
      setSelectedImage(null)
      setPreviewUrl('')
      setImageMessage('The image must be 5 MB or smaller.')
      setImageMessageType('error')
      event.target.value = ''
      return
    }

    setSelectedImage(file)
    setPreviewUrl(URL.createObjectURL(file))
    setImageMessage(`Image selected: ${file.name}`)
    setImageMessageType('success')
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const created = await onSubmit({ ...form, type }, selectedImage)
    if (created) {
      setForm(EMPTY_FORM)
      setSelectedImage(null)
      setPreviewUrl('')
      setImageMessage('')
      if (imageInputRef.current) imageInputRef.current.value = ''
    }
  }

  return (
    <form className="report-form" onSubmit={handleSubmit}>
      <div className="form-grid">
        <label>
          Item name *
          <input name="title" value={form.title} onChange={handleChange} required maxLength="150" />
        </label>
        <label>
          Category *
          <input name="category" value={form.category} onChange={handleChange} required maxLength="100" placeholder="For example, Electronics" />
        </label>
        <label>
          Location *
          <input name="location" value={form.location} onChange={handleChange} required maxLength="255" placeholder="Where was it lost or found?" />
        </label>
        <label>
          Date
          <input name="date" type="date" value={form.date} onChange={handleChange} />
        </label>
        <label>
          Color
          <input name="color" value={form.color} onChange={handleChange} maxLength="100" />
        </label>
        <label>
          Brand
          <input name="brand" value={form.brand} onChange={handleChange} maxLength="100" />
        </label>
        <label>
          Your name *
          <input name="contactName" value={form.contactName} onChange={handleChange} required maxLength="150" />
        </label>
        <label>
          Phone number *
          <input name="contactPhone" type="tel" value={form.contactPhone} onChange={handleChange} required maxLength="40" />
        </label>
        <label className="full-width">
          Item image (optional)
          <input
            ref={imageInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
            onChange={handleImageChange}
          />
          {imageMessage && (
            <span className={`image-upload-message ${imageMessageType}`} role={imageMessageType === 'error' ? 'alert' : 'status'}>
              {imageMessage}
            </span>
          )}
          {previewUrl && <img className="image-upload-preview" src={previewUrl} alt="Selected item preview" />}
        </label>
        <label className="full-width">
          Description *
          <textarea name="description" value={form.description} onChange={handleChange} required maxLength="2000" rows="4" />
        </label>
      </div>
      <button className="button button-primary" type="submit" disabled={busy}>
        {busy ? 'Submitting…' : `Submit ${type.toLowerCase()} report`}
      </button>
    </form>
  )
}

function ReportImage({ item, className = 'item-image' }) {
  const [failedSource, setFailedSource] = useState('')
  const source = item.imageUrl?.trim()

  if (!source || failedSource === source) {
    return (
      <div className={`${className} item-image-fallback`} role="img" aria-label={`No image available for ${item.title}`}>
        <span className="image-fallback-icon" aria-hidden="true">◇</span>
        <span>No image available</span>
      </div>
    )
  }

  return (
    <img
      className={className}
      src={source}
      alt={item.title}
      loading="lazy"
      onError={() => setFailedSource(source)}
    />
  )
}

function ItemCard({ item, onSelect, onStatusChange, onDelete, canClaim, onClaim }) {
  return (
    <article className="item-card">
      <ReportImage item={item} />
      <div className="item-card-top">
        <span className={`type-badge ${item.type.toLowerCase()}`}>{item.type}</span>
        <span className={`status-badge ${item.status.toLowerCase()}`}>{item.status}</span>
      </div>
      <h3>{item.title}</h3>
      <p className="item-summary">{item.description}</p>
      <p className="item-meta">{item.category} · {item.location}</p>
      <p className="item-meta">{formatDate(item.date)}</p>
      <div className="card-actions">
        <button className="button button-light" type="button" onClick={() => onSelect(item)}>Details</button>
        {canClaim && item.type === 'FOUND' && item.status === 'ACTIVE' && (
          <button className="button button-outline" type="button" onClick={() => onClaim(item)}>Claim Item</button>
        )}
        <label className="status-control">
          Status
          <select value={item.status} onChange={(event) => onStatusChange(item, event.target.value)}>
            <option value="ACTIVE">Active</option>
            <option value="CLAIMED">Claimed</option>
            <option value="RESOLVED">Resolved</option>
          </select>
        </label>
        <button className="button button-danger" type="button" onClick={() => onDelete(item)}>Delete</button>
      </div>
      {item.type === 'LOST' && <AIMatchPanel item={item} canClaim={canClaim} onClaim={onClaim} />}
      <DuplicateDetectionPanel item={item} />
    </article>
  )
}

function DuplicateDetectionPanel({ item }) {
  const [duplicates, setDuplicates] = useState(null)
  const [loadingDuplicates, setLoadingDuplicates] = useState(false)
  const [duplicateError, setDuplicateError] = useState('')

  async function checkDuplicates() {
    setLoadingDuplicates(true)
    setDuplicateError('')
    setDuplicates(null)
    try {
      const results = await request(`${API_URL}/${item.id}/duplicates`)
      setDuplicates(results)
    } catch (requestError) {
      setDuplicateError(requestError.status === 404
        ? 'This item could not be found. It may have been removed.'
        : `Could not check for duplicates: ${requestError.message}`)
    } finally {
      setLoadingDuplicates(false)
    }
  }

  return (
    <div className="duplicate-panel">
      <button
        className="button button-outline"
        type="button"
        onClick={checkDuplicates}
        disabled={loadingDuplicates}
      >
        {loadingDuplicates ? 'Checking…' : 'Check Duplicates'}
      </button>
      {loadingDuplicates && <p className="match-message" role="status">Checking similar reports…</p>}
      {duplicateError && <p className="match-error" role="alert">{duplicateError}</p>}
      {duplicates && (
        <div className="duplicate-results" aria-live="polite">
          <h4>Possible Duplicate Reports</h4>
          {duplicates.length === 0 ? (
            <p className="match-message">No possible duplicate reports found.</p>
          ) : (
            <>
              <p className="duplicate-warning">Possible duplicate reports detected.</p>
              {duplicates.map((duplicate) => (
                <article className="duplicate-card" key={duplicate.id ?? duplicate.itemId}>
                  <div className="ai-match-heading">
                    <h5>{duplicate.title}</h5>
                    <span className="match-score">{duplicate.similarityScore}% Similarity</span>
                  </div>
                  <p className="item-meta">Type: {duplicate.type}</p>
                  <p className="item-meta">Location: {duplicate.location || 'Not specified'}</p>
                  <p className="item-meta">Date: {formatDate(duplicate.date)}</p>
                </article>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  )
}

function ClaimDialog({ item, busy, onCancel, onSubmit }) {
  const [verificationDetails, setVerificationDetails] = useState('')
  const [claimError, setClaimError] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    setClaimError('')
    try {
      await onSubmit(item, verificationDetails)
    } catch (submitError) {
      setClaimError(submitError.message)
    }
  }

  return (
    <div className="claim-modal-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !busy) onCancel()
    }}>
      <section className="claim-modal" role="dialog" aria-modal="true" aria-labelledby="claim-modal-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">OWNERSHIP CLAIM</p>
            <h2 id="claim-modal-title">Claim {item.title}</h2>
          </div>
        </div>
        <form className="claim-form" onSubmit={handleSubmit}>
          <label>
            Verification Details
            <textarea
              value={verificationDetails}
              onChange={(event) => setVerificationDetails(event.target.value)}
              required
              maxLength="2000"
              rows="5"
              placeholder="Describe details that can help verify this item belongs to you."
            />
          </label>
          {claimError && <p className="message message-error" role="alert">{claimError}</p>}
          <div className="claim-actions">
            <button className="button button-primary" type="submit" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit Claim'}
            </button>
            <button className="button button-light" type="button" onClick={onCancel} disabled={busy}>Cancel</button>
          </div>
        </form>
      </section>
    </div>
  )
}

function AIMatchPanel({ item, canClaim, onClaim }) {
  const [matches, setMatches] = useState(null)
  const [loadingMatches, setLoadingMatches] = useState(false)
  const [matchError, setMatchError] = useState('')

  async function findMatches() {
    setLoadingMatches(true)
    setMatchError('')
    try {
      const results = await request(`${API_URL}/${item.id}/matches`)
      setMatches(results)
    } catch (requestError) {
      setMatchError(requestError.message)
      setMatches(null)
    } finally {
      setLoadingMatches(false)
    }
  }

  return (
    <div className="ai-match-panel">
      <button className="button button-outline" type="button" onClick={findMatches} disabled={loadingMatches}>
        {loadingMatches ? 'Finding matches…' : 'Find AI Matches'}
      </button>
      {loadingMatches && <p className="match-message" role="status">Searching found reports…</p>}
      {matchError && <p className="match-error" role="alert">Could not load possible matches: {matchError}</p>}
      {matches && (
        <div className="ai-match-results" aria-live="polite">
          <h4>AI Possible Matches</h4>
          {matches.length === 0 ? (
            <p className="match-message">No possible matches found.</p>
          ) : matches.map((match) => (
            <div className="ai-match-card" key={match.foundItem.id}>
              <ReportImage item={match.foundItem} className="ai-match-image" />
              <div className="ai-match-heading">
                <h5>{match.foundItem.title}</h5>
                <span className="match-score">{match.similarityScore}% Match</span>
              </div>
              <p className="item-summary">{match.foundItem.description}</p>
              <p className="item-meta">Category: {match.foundItem.category || 'Not specified'}</p>
              <p className="item-meta">
                Color: {match.foundItem.color || 'Not specified'}
                {' · '}Brand: {match.foundItem.brand || 'Not specified'}
              </p>
              <p className="item-meta">Location: {match.foundItem.location || 'Not specified'}</p>
              <p className="item-meta">Date: {formatDate(match.foundItem.date)}</p>
              {canClaim && match.foundItem.status === 'ACTIVE' && (
                <button
                  className="button button-outline"
                  type="button"
                  onClick={() => onClaim({ ...match.foundItem, lostItemId: item.id })}
                >
                  Claim Item
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function AuthPanel({ user, onLogin, onLogout }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ name: '', email: '', phone: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    setNotice('')

    const isRegistering = mode === 'register'
    const endpoint = isRegistering ? `${AUTH_API_URL}/register` : `${AUTH_API_URL}/login`
    const payload = isRegistering
      ? { name: form.name, email: form.email, phone: form.phone, password: form.password }
      : { email: form.email, password: form.password }

    try {
      const result = await request(endpoint, {
        method: 'POST',
        body: JSON.stringify(payload),
      })

      if (isRegistering) {
        setForm((current) => ({ ...current, password: '' }))
        setMode('login')
        setNotice('Registration successful. You can now log in.')
      } else {
        onLogin(result)
      }
    } catch (authError) {
      setError(authError.message)
    } finally {
      setBusy(false)
    }
  }

  if (user) {
    return (
      <div className="auth-account">
        <div>
          <strong>Signed in as {user.name}</strong>
          <span>{user.role}</span>
        </div>
        <button className="button button-light" type="button" onClick={onLogout}>Logout</button>
      </div>
    )
  }

  return (
    <section className="auth-section" aria-labelledby="auth-title">
      <div className="auth-heading">
        <div>
          <p className="eyebrow">YOUR CAMPUS ACCOUNT</p>
          <h2 id="auth-title">{mode === 'login' ? 'Welcome back' : 'Create an account'}</h2>
        </div>
        <div className="auth-tabs" role="tablist" aria-label="Account action">
          <button
            className={`auth-tab ${mode === 'login' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={mode === 'login'}
            onClick={() => { setMode('login'); setError(''); setNotice('') }}
          >
            Login
          </button>
          <button
            className={`auth-tab ${mode === 'register' ? 'active' : ''}`}
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            onClick={() => { setMode('register'); setError(''); setNotice('') }}
          >
            Register
          </button>
        </div>
      </div>
      {error && <p className="auth-message auth-error" role="alert">{error}</p>}
      {notice && <p className="auth-message auth-success" role="status">{notice}</p>}
      <form className="auth-form" onSubmit={handleSubmit}>
        {mode === 'register' && (
          <>
            <label>
              Name
              <input name="name" value={form.name} onChange={handleChange} required autoComplete="name" />
            </label>
            <label>
              Phone
              <input name="phone" type="tel" value={form.phone} onChange={handleChange} required autoComplete="tel" />
            </label>
          </>
        )}
        <label>
          Email
          <input name="email" type="email" value={form.email} onChange={handleChange} required autoComplete="email" />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            value={form.password}
            onChange={handleChange}
            required
            minLength={mode === 'register' ? 6 : undefined}
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
          />
        </label>
        <button className="button button-primary auth-submit" type="submit" disabled={busy}>
          {busy ? 'Please wait…' : mode === 'login' ? 'Login' : 'Register'}
        </button>
      </form>
    </section>
  )
}

function App() {
  const [activePage, setActivePage] = useState('dashboard')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [items, setItems] = useState([])
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [globalSearchInput, setGlobalSearchInput] = useState('')
  const [globalSearchQuery, setGlobalSearchQuery] = useState('')
  const [selectedItem, setSelectedItem] = useState(null)
  const [claimItem, setClaimItem] = useState(null)
  const [claimBusy, setClaimBusy] = useState(false)
  const [adminClaims, setAdminClaims] = useState([])
  const [userClaims, setUserClaims] = useState([])
  const [userClaimsLoading, setUserClaimsLoading] = useState(false)
  const [userClaimsError, setUserClaimsError] = useState('')
  const [adminClaimsLoading, setAdminClaimsLoading] = useState(false)
  const [adminClaimsError, setAdminClaimsError] = useState('')
  const [claimStatusBusyId, setClaimStatusBusyId] = useState(null)
  const [claimStatusError, setClaimStatusError] = useState('')
  const [resolvedItemBusyId, setResolvedItemBusyId] = useState(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [authUser, setAuthUser] = useState(readStoredUser)
  const latestLoadRequest = useRef(0)
  const latestAdminClaimsRequest = useRef(0)
  const pollingRequestInFlightRef = useRef(false)
  const filtersRef = useRef(filters)
  const resultsRef = useRef(null)
  const noticeRef = useRef(null)

  useEffect(() => {
    filtersRef.current = filters
  }, [filters])

  const loadItems = useCallback(async (activeFilters = EMPTY_FILTERS, { silent = false, query = '' } = {}) => {
    const requestId = ++latestLoadRequest.current
    if (!silent) {
      setLoading(true)
      setError('')
    }
    try {
      const params = new URLSearchParams()
      Object.entries(activeFilters).forEach(([key, value]) => {
        const normalizedValue = value.trim()
        if (normalizedValue) params.set(key, normalizedValue)
      })
      const hasQuery = query.trim().length > 0
      const endpoint = hasQuery
        ? API_URL
        : [...params].length ? `${API_URL}/search?${params.toString()}` : API_URL
      const responseItems = await request(endpoint)
      const results = hasQuery
        ? responseItems.filter((item) => {
          const matchesFilters = (
            (!activeFilters.type || item.type === activeFilters.type)
            && (!activeFilters.status || item.status === activeFilters.status)
            && (!activeFilters.category || item.category?.toLocaleLowerCase().includes(activeFilters.category.trim().toLocaleLowerCase()))
            && (!activeFilters.location || item.location?.toLocaleLowerCase().includes(activeFilters.location.trim().toLocaleLowerCase()))
          )
          return matchesFilters && itemMatchesSearchQuery(item, query)
        })
        : responseItems
      if (requestId === latestLoadRequest.current) {
        setItems(results)
        setSelectedItem((current) => {
          if (!current) return current
          return results.find((item) => item.id === current.id) ?? current
        })
      }
      return requestId === latestLoadRequest.current
    } catch (loadError) {
      if (!silent && requestId === latestLoadRequest.current) {
        setError(loadError.message)
      }
      return false
    } finally {
      if (!silent && requestId === latestLoadRequest.current) {
        setLoading(false)
      }
    }
  }, [])

  const loadAdminClaims = useCallback(async ({ silent = false } = {}) => {
    if (authUser?.role !== 'ADMIN') return

    const requestId = ++latestAdminClaimsRequest.current
    if (!silent) {
      setAdminClaimsLoading(true)
      setAdminClaimsError('')
    }
    try {
      const allItems = await request(API_URL)
      const itemIds = [...new Set(allItems.map((item) => item.id))]
      const claimLists = await Promise.all(
        itemIds.map((itemId) => request(`${BACKEND_URL}/api/claims/item/${itemId}`))
      )
      const itemStatuses = new Map(allItems.map((item) => [item.id, item.status]))
      const claims = claimLists.flat().map((claim) => ({
        ...claim,
        itemStatus: itemStatuses.get(claim.itemId),
      })).sort((first, second) =>
        new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime()
      )
      if (requestId === latestAdminClaimsRequest.current) setAdminClaims(claims)
    } catch (claimsError) {
      if (!silent && requestId === latestAdminClaimsRequest.current) {
        setAdminClaimsError(formatClaimError(claimsError))
      }
    } finally {
      if (!silent && requestId === latestAdminClaimsRequest.current) setAdminClaimsLoading(false)
    }
  }, [authUser])

  const loadUserClaims = useCallback(async ({ silent = false } = {}) => {
    if (!authUser?.id || authUser.role === 'ADMIN') return

    if (!silent) {
      setUserClaimsLoading(true)
      setUserClaimsError('')
    }
    try {
      const claims = await request(`${BACKEND_URL}/api/claims/user/${authUser.id}`)
      setUserClaims(claims)
    } catch (claimsError) {
      if (!silent) setUserClaimsError(claimsError.message)
    } finally {
      if (!silent) setUserClaimsLoading(false)
    }
  }, [authUser])

  useEffect(() => {
    let isMounted = true

    async function refreshServerData(silent, allowHidden = false) {
      if (!isMounted || (!allowHidden && document.visibilityState !== 'visible')
          || pollingRequestInFlightRef.current) return
      pollingRequestInFlightRef.current = true
      try {
        await Promise.all([
          loadItems(activePage === 'dashboard' ? EMPTY_FILTERS : filtersRef.current, {
            silent,
            query: activePage === 'dashboard' ? '' : globalSearchQuery,
          }),
          authUser?.role === 'ADMIN' ? loadAdminClaims({ silent }) : Promise.resolve(),
          authUser && authUser.role !== 'ADMIN' ? loadUserClaims({ silent }) : Promise.resolve(),
        ])
      } finally {
        pollingRequestInFlightRef.current = false
      }
    }

    refreshServerData(false, true)
    const intervalId = window.setInterval(
      () => refreshServerData(true),
      DATA_REFRESH_INTERVAL_MS
    )
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') refreshServerData(true)
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      isMounted = false
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [activePage, authUser, globalSearchQuery, loadAdminClaims, loadItems, loadUserClaims])

  useEffect(() => {
    function handleSessionExpired() {
      setAuthUser(null)
      setClaimItem(null)
      setAdminClaims([])
      setUserClaims([])
      setUserClaimsError('')
      setAdminClaimsError('')
      setClaimStatusError('')
      setError('Your session expired or is no longer valid. Please log in again.')
    }

    function handleStorageChange(event) {
      if (event.key && event.key !== TOKEN_STORAGE_KEY && event.key !== USER_STORAGE_KEY) return
      window.setTimeout(() => {
        setAuthUser(readStoredUser())
        setClaimItem(null)
        setAdminClaims([])
        setUserClaims([])
        setUserClaimsError('')
        setAdminClaimsError('')
        setClaimStatusError('')
        setError('')
        setNotice('')
      }, 0)
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
    window.addEventListener('storage', handleStorageChange)
    return () => {
      window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired)
      window.removeEventListener('storage', handleStorageChange)
    }
  }, [])

  async function createReport(report, imageFile) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      const createdItem = await request(API_URL, { method: 'POST', body: JSON.stringify(report) })
      let imageUploadError = ''

      if (imageFile) {
        try {
          if (!createdItem?.id) throw new Error('The new report ID was not returned.')
          const imageData = new FormData()
          imageData.append('image', imageFile)
          await request(`${API_URL}/${createdItem.id}/image`, {
            method: 'POST',
            body: imageData,
          })
        } catch (uploadError) {
          imageUploadError = uploadError.message
        }
      }

      await loadItems(filters, { query: globalSearchQuery })
      if (imageUploadError) {
        setError(`The report was created, but the image upload failed: ${imageUploadError}`)
      } else if (imageFile) {
        setNotice(`${report.type === 'LOST' ? 'Lost' : 'Found'} report submitted and image uploaded successfully.`)
      } else {
        setNotice(`${report.type === 'LOST' ? 'Lost' : 'Found'} report submitted.`)
      }
      return true
    } catch (submitError) {
      setError(submitError.message)
      return false
    } finally {
      setBusy(false)
    }
  }

  async function updateStatus(item, status) {
    setError('')
    setNotice('')
    try {
      const updated = await request(`${API_URL}/${item.id}`, {
        method: 'PUT',
        body: JSON.stringify({ ...item, status }),
      })
      setItems((current) => current.map((entry) => entry.id === updated.id ? updated : entry))
      if (selectedItem?.id === updated.id) setSelectedItem(updated)
      setNotice('Item status updated.')
    } catch (updateError) {
      setError(updateError.message)
    }
  }

  async function deleteItem(item) {
    if (!window.confirm(`Delete the report for "${item.title}"?`)) return
    setError('')
    setNotice('')
    try {
      await request(`${API_URL}/${item.id}`, { method: 'DELETE' })
      setItems((current) => current.filter((entry) => entry.id !== item.id))
      if (selectedItem?.id === item.id) setSelectedItem(null)
      setNotice('Report deleted.')
    } catch (deleteError) {
      setError(deleteError.message)
    }
  }

  async function submitClaim(item, verificationDetails) {
    setClaimBusy(true)
    try {
      await request(`${BACKEND_URL}/api/claims`, {
        method: 'POST',
        body: JSON.stringify({
          itemId: item.id,
          ...(item.lostItemId ? { lostItemId: item.lostItemId } : {}),
          verificationDetails: verificationDetails.trim(),
        }),
      })
      setClaimItem(null)
      setError('')
      setNotice('Claim submitted successfully. Your claim is pending review.')
      if (authUser?.role === 'ADMIN') {
        await loadAdminClaims({ silent: true })
      }
      window.requestAnimationFrame(() => {
        noticeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    } catch (claimError) {
      if (claimError.status === 409) {
        throw new Error('You already have an active claim for this item.')
      }
      if (claimError.status === 404) {
        throw new Error('This found item could not be found. It may have been removed.')
      }
      throw new Error(`Could not submit your claim: ${claimError.message}`)
    } finally {
      setClaimBusy(false)
    }
  }

  async function updateClaimStatus(claimId, status) {
    setClaimStatusBusyId(claimId)
    setClaimStatusError('')
    setNotice('')
    try {
      await request(`${BACKEND_URL}/api/claims/${claimId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status }),
      })
      setNotice(`Claim ${status === 'APPROVED' ? 'approved' : 'rejected'} successfully.`)
      await loadItems(filters, { query: globalSearchQuery })
      await loadAdminClaims()
    } catch (statusError) {
      setClaimStatusError(formatClaimError(statusError))
    } finally {
      setClaimStatusBusyId(null)
    }
  }

  async function markItemResolved(itemId) {
    setResolvedItemBusyId(itemId)
    setClaimStatusError('')
    setNotice('')
    try {
      const resolvedItem = await request(`${API_URL}/${itemId}/resolve`, { method: 'PUT' })
      const linkedLostItemIds = adminClaims
        .filter((claim) =>
          claim.itemId === itemId
          && claim.status === 'APPROVED'
          && claim.lostItemId != null
        )
        .map((claim) => claim.lostItemId)
      const resolvedItemIds = new Set([itemId, ...linkedLostItemIds])

      setItems((current) => current.map((item) =>
        resolvedItemIds.has(item.id)
          ? { ...item, status: resolvedItem.status }
          : item
      ))
      setSelectedItem((current) =>
        current && resolvedItemIds.has(current.id)
          ? { ...current, status: resolvedItem.status }
          : current
      )
      setNotice('Item marked as resolved.')
      await loadItems(filters, { query: globalSearchQuery })
      await loadAdminClaims()
    } catch (resolveError) {
      setClaimStatusError(resolveError.message || 'Could not mark the item as resolved.')
    } finally {
      setResolvedItemBusyId(null)
    }
  }

  function handleFilterChange(event) {
    const { name, value } = event.target
    setFilters((current) => ({ ...current, [name]: value }))
  }

  async function handleSearch(event) {
    event.preventDefault()
    const loaded = await loadItems(filters, { query: globalSearchQuery })
    if (loaded) {
      resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS)
    setGlobalSearchInput('')
    setGlobalSearchQuery('')
    loadItems(EMPTY_FILTERS)
  }

  function handleGlobalSearch(event) {
    event.preventDefault()
    submitGlobalSearch(globalSearchInput)
  }

  function submitGlobalSearch(searchValue) {
    const query = searchValue.trim()
    setGlobalSearchInput(query)
    setGlobalSearchQuery(query)
    navigateTo('lost-found')
    loadItems(filters, { query }).then((loaded) => {
      if (loaded) {
        window.requestAnimationFrame(() => {
          resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        })
      }
    })
  }

  function clearGlobalSearch() {
    setGlobalSearchInput('')
    setGlobalSearchQuery('')
    navigateTo('lost-found')
  }

  const lostItems = items.filter((item) => item.type === 'LOST')
  const foundItems = items.filter((item) => item.type === 'FOUND')
  const matchingLostItems = lostItems.filter((item) => itemMatchesSearchQuery(item, globalSearchQuery))
  const matchingFoundItems = foundItems.filter((item) => itemMatchesSearchQuery(item, globalSearchQuery))
  const statusCounts = {
    ACTIVE: items.filter((item) => item.status === 'ACTIVE').length,
    CLAIMED: items.filter((item) => item.status === 'CLAIMED').length,
    RESOLVED: items.filter((item) => item.status === 'RESOLVED').length,
  }
  const recentItems = [...items]
    .sort((first, second) => new Date(second.date || 0).getTime() - new Date(first.date || 0).getTime())
    .slice(0, 4)
  const showLostItems = filters.type !== 'FOUND'
  const showFoundItems = filters.type !== 'LOST'
  const pageTitles = {
    dashboard: 'Dashboard',
    'lost-found': 'Lost & Found',
    'my-reports': 'My Reports',
    'ai-matches': 'AI Matches',
    claims: 'Claims',
    notifications: 'Notifications',
    profile: 'Profile',
    'report-lost': 'Report a Lost Item',
    'report-found': 'Report a Found Item',
  }

  function navigateTo(page) {
    setActivePage(page)
    setSidebarOpen(false)
  }

  function handleLogin(result) {
    const user = {
      id: result.id,
      name: result.name,
      email: result.email,
      role: result.role,
    }
    localStorage.setItem(TOKEN_STORAGE_KEY, result.token)
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
    setAuthUser(user)
    setAdminClaims([])
    setUserClaims([])
    setUserClaimsError('')
    setAdminClaimsError('')
    setClaimStatusError('')
    setNotice('Login successful.')
    setError('')
  }

  function handleLogout() {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
    setAuthUser(null)
    setClaimItem(null)
    setAdminClaims([])
    setUserClaims([])
    setUserClaimsError('')
    setAdminClaimsError('')
    setClaimStatusError('')
    setNotice('You have been logged out.')
    setError('')
  }

  return (
    <div className="app-shell">
      <button
        className={`sidebar-scrim${sidebarOpen ? ' visible' : ''}`}
        type="button"
        aria-label="Close navigation menu"
        onClick={() => setSidebarOpen(false)}
      />
      <aside className={`app-sidebar${sidebarOpen ? ' open' : ''}`}>
        <a className="sidebar-brand" href="#dashboard" onClick={(event) => { event.preventDefault(); navigateTo('dashboard') }}>
          <span className="brand-mark">CF</span>
          <span><strong>Campus Find</strong><small>Lost &amp; Found</small></span>
        </a>
        <p className="sidebar-label">WORKSPACE</p>
        <nav className="sidebar-navigation" aria-label="Main navigation">
          {[
            ['dashboard', '⌂', 'Dashboard'],
            ['lost-found', '◇', 'Lost & Found'],
            ['my-reports', '▤', 'My Reports'],
            ['ai-matches', '✧', 'AI Matches'],
            ['claims', '✓', 'Claims'],
            ['notifications', '◉', 'Notifications'],
            ['profile', '○', 'Profile'],
          ].map(([page, icon, label]) => (
            <button
              className={`sidebar-link${activePage === page ? ' active' : ''}`}
              key={page}
              type="button"
              aria-current={activePage === page ? 'page' : undefined}
              onClick={() => navigateTo(page)}
            >
              <span className="sidebar-icon" aria-hidden="true">{icon}</span>
              <span>{label}</span>
              {page === 'claims' && authUser?.role === 'ADMIN' && (
                <span className="sidebar-count">{adminClaims.filter((claim) => claim.status === 'PENDING').length}</span>
              )}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-user">
            <span className="user-avatar">{authUser?.name?.slice(0, 1).toUpperCase() || 'G'}</span>
            <span><strong>{authUser?.name || 'Guest user'}</strong><small>{authUser?.role || 'Not signed in'}</small></span>
          </div>
          <button className="sidebar-link logout-link" type="button" onClick={authUser ? handleLogout : () => navigateTo('profile')}>
            <span className="sidebar-icon" aria-hidden="true">↪</span>
            <span>{authUser ? 'Logout' : 'Sign in'}</span>
          </button>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="mobile-menu-button"
              type="button"
              aria-label={sidebarOpen ? 'Close navigation menu' : 'Open navigation menu'}
              aria-expanded={sidebarOpen}
              onClick={() => setSidebarOpen((open) => !open)}
            >
              <span aria-hidden="true">{sidebarOpen ? '×' : '☰'}</span>
            </button>
            <div className="breadcrumbs"><span>Workspace</span><span aria-hidden="true">/</span><strong>{pageTitles[activePage]}</strong></div>
          </div>
          <form className="topbar-search" role="search" onSubmit={handleGlobalSearch}>
            <span aria-hidden="true">⌕</span>
            <input
              type="search"
              aria-label="Search reports"
              value={globalSearchInput}
              onChange={(event) => setGlobalSearchInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
                  event.preventDefault()
                  submitGlobalSearch(event.currentTarget.value)
                }
              }}
              placeholder="Search reports..."
            />
            {globalSearchInput && (
              <button className="topbar-search-clear" type="button" onClick={clearGlobalSearch} aria-label="Clear search">
                ×
              </button>
            )}
            <button className="topbar-search-submit" type="submit" aria-label="Search reports">Search</button>
          </form>
          <div className="topbar-account">
            {authUser && activePage !== 'notifications' ? (
              <NotificationBell key={authUser.id} userId={authUser.id} />
            ) : !authUser ? (
              <button className="notification-bell" type="button" aria-label="Sign in to view notifications" onClick={() => navigateTo('profile')}>♧</button>
            ) : null}
            <button className="topbar-user" type="button" onClick={() => navigateTo('profile')}>
              <span className="user-avatar">{authUser?.name?.slice(0, 1).toUpperCase() || 'G'}</span>
              <span><strong>{authUser?.name || 'Guest'}</strong><small>{authUser?.role || 'Sign in to your account'}</small></span>
              <span className="account-chevron" aria-hidden="true">⌄</span>
            </button>
          </div>
        </header>

        <main className="main-content">
          {(error || notice) && (
            <div className="global-messages" aria-live="polite">
              {error && <div className="message message-error" role="alert">{error}</div>}
              {notice && <div ref={noticeRef} className="message message-success" role="status">{notice}</div>}
            </div>
          )}

          <section className="page-view dashboard-page" hidden={activePage !== 'dashboard'}>
            <div className="welcome-heading">
              <div><p className="eyebrow">CAMPUS FIND WORKSPACE</p><h1>Good to see you{authUser?.name ? `, ${authUser.name.split(' ')[0]}` : ''}.</h1><p>Here’s what’s happening across your community today.</p></div>
              <span className="dashboard-date">{new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}</span>
            </div>
            <div className="dashboard-stats" aria-label="Report statistics">
              {[
                ['Total Reports', items.length, 'Across lost and found'],
                ['Active', statusCounts.ACTIVE, 'Awaiting a match'],
                ['Claimed', statusCounts.CLAIMED, 'In the return process'],
                ['Resolved', statusCounts.RESOLVED, 'Successfully reunited'],
              ].map(([label, count, caption]) => (
                <article className="stat-card" key={label}>
                  <div className={`stat-icon stat-icon-${label.toLowerCase().replace(' ', '-')}`} aria-hidden="true">{label === 'Total Reports' ? '▤' : label === 'Active' ? '◷' : label === 'Claimed' ? '✓' : '↗'}</div>
                  <div><span className="stat-label">{label}</span><strong>{count}</strong><small>{caption}</small></div>
                </article>
              ))}
            </div>

            <div className="dashboard-columns">
              <section className="dashboard-panel overview-panel">
                <div className="panel-heading"><div><h2>Reports overview</h2><p>Current status across {items.length} reports</p></div><span className="overview-total">{items.length} total</span></div>
                <div className="status-summary">
                  {[
                    ['Active', statusCounts.ACTIVE, 'active'],
                    ['Claimed', statusCounts.CLAIMED, 'claimed'],
                    ['Resolved', statusCounts.RESOLVED, 'resolved'],
                  ].map(([label, count, cssStatus]) => (
                    <div className="summary-row" key={label}>
                      <div className="summary-label"><span className={`summary-dot ${cssStatus}`} />{label}<strong>{count}</strong></div>
                      <div className="summary-track"><span className={`summary-fill ${cssStatus}`} style={{ width: `${items.length ? (count / items.length) * 100 : 0}%` }} /></div>
                    </div>
                  ))}
                </div>
              </section>
              <section className="dashboard-panel quick-actions-panel">
                <div className="panel-heading"><div><h2>Quick actions</h2><p>What would you like to do?</p></div></div>
                <button className="quick-action" type="button" onClick={() => navigateTo('report-lost')}><span className="quick-action-icon lost-action">−</span><span><strong>Report a lost item</strong><small>Tell the community what’s missing</small></span><span aria-hidden="true">→</span></button>
                <button className="quick-action" type="button" onClick={() => navigateTo('report-found')}><span className="quick-action-icon found-action">+</span><span><strong>Report a found item</strong><small>Help return an item to its owner</small></span><span aria-hidden="true">→</span></button>
                <button className="quick-action" type="button" onClick={() => navigateTo('ai-matches')}><span className="quick-action-icon match-action">✧</span><span><strong>View AI matches</strong><small>Find possible matches for lost items</small></span><span aria-hidden="true">→</span></button>
              </section>
            </div>

            <section className="dashboard-panel recent-panel">
              <div className="panel-heading"><div><h2>Recent lost &amp; found items</h2><p>Recently reported items from the community</p></div><button className="text-action" type="button" onClick={() => navigateTo('lost-found')}>View all reports <span aria-hidden="true">→</span></button></div>
              {loading ? <p className="empty-state">Loading reports…</p> : recentItems.length ? (
                <div className="item-grid dashboard-item-grid">
                  {recentItems.map((item) => <ItemCard key={item.id} item={item} onSelect={setSelectedItem} onStatusChange={updateStatus} onDelete={deleteItem} canClaim={Boolean(authUser)} onClaim={setClaimItem} />)}
                </div>
              ) : <p className="empty-state">No reports have been submitted yet.</p>}
            </section>
          </section>

          <section className="page-view listings-page" hidden={activePage !== 'lost-found'}>
            <div className="page-heading">
              <div><p className="eyebrow">COMMUNITY BOARD</p><h1>Lost &amp; Found</h1><p>Search reports and help reunite belongings with their owners.</p></div>
              <div className="page-heading-actions"><button className="button button-outline" type="button" onClick={() => navigateTo('report-lost')}>− Report lost</button><button className="button button-primary" type="button" onClick={() => navigateTo('report-found')}>＋ Report found</button></div>
            </div>
            <section className="dashboard-panel filter-panel" id="search">
              <form className="search-form" onSubmit={handleSearch}>
                <label>Category<input name="category" value={filters.category} onChange={handleFilterChange} placeholder="e.g. Keys" /></label>
                <label>Location<input name="location" value={filters.location} onChange={handleFilterChange} placeholder="e.g. Library" /></label>
                <label>Type<select name="type" value={filters.type} onChange={handleFilterChange}><option value="">All types</option><option value="LOST">Lost</option><option value="FOUND">Found</option></select></label>
                <label>Status<select name="status" value={filters.status} onChange={handleFilterChange}><option value="">All statuses</option><option value="ACTIVE">Active</option><option value="CLAIMED">Claimed</option><option value="RESOLVED">Resolved</option></select></label>
                <div className="search-actions"><button className="button button-primary" type="submit">Apply filters</button><button className="button button-light" type="button" onClick={clearFilters}>Clear</button></div>
              </form>
            </section>
            {globalSearchQuery && (
              <div className="search-result-summary" role="status">
                <span>
                  {loading ? 'Searching reports for ' : `${matchingLostItems.length + matchingFoundItems.length} ${(matchingLostItems.length + matchingFoundItems.length) === 1 ? 'report' : 'reports'} found for `}
                  <strong>“{globalSearchQuery}”</strong>
                </span>
                <button className="text-action" type="button" onClick={clearGlobalSearch}>Clear search</button>
              </div>
            )}
            <div id="item-listings" ref={resultsRef}>
              {showLostItems && <section className="listing-section" id="reports">
                <div className="section-heading"><div><p className="eyebrow">MISSING SOMETHING?</p><h2>Lost items</h2></div><span className="result-count">{lostItems.length} reports</span></div>
                {loading ? <p className="empty-state">Loading reports…</p> : <div className="item-grid">{matchingLostItems.map((item) => <ItemCard key={item.id} item={item} onSelect={setSelectedItem} onStatusChange={updateStatus} onDelete={deleteItem} canClaim={Boolean(authUser)} onClaim={setClaimItem} />)}{!matchingLostItems.length && <p className="empty-state">{globalSearchQuery ? `No lost reports match “${globalSearchQuery}” and the selected filters.` : 'No lost reports match your search.'}</p>}</div>}
              </section>}
              {showFoundItems && <section className="listing-section">
                <div className="section-heading"><div><p className="eyebrow">READY TO BE REUNITED</p><h2>Found items</h2></div><span className="result-count">{foundItems.length} reports</span></div>
                {loading ? <p className="empty-state">Loading reports…</p> : <div className="item-grid">{matchingFoundItems.map((item) => <ItemCard key={item.id} item={item} onSelect={setSelectedItem} onStatusChange={updateStatus} onDelete={deleteItem} canClaim={Boolean(authUser)} onClaim={setClaimItem} />)}{!matchingFoundItems.length && <p className="empty-state">{globalSearchQuery ? `No found reports match “${globalSearchQuery}” and the selected filters.` : 'No found reports match your search.'}</p>}</div>}
              </section>}
            </div>
          </section>

          <section className="page-view ai-page" hidden={activePage !== 'ai-matches'}>
            <div className="page-heading"><div><p className="eyebrow">SMART DISCOVERY</p><h1>AI Matches</h1><p>Find found-item reports that may match a lost report.</p></div><button className="button button-light" type="button" onClick={() => navigateTo('lost-found')}>Browse all reports</button></div>
            {filters.type === 'FOUND' && <div className="message message-info"><span>Your current filter only shows found reports.</span><button className="text-action" type="button" onClick={() => { const nextFilters = { ...filters, type: '' }; setFilters(nextFilters); loadItems(nextFilters, { query: globalSearchQuery }) }}>Show lost reports for matching</button></div>}
            {loading ? <p className="empty-state">Loading lost reports…</p> : lostItems.length ? <div className="item-grid">{lostItems.map((item) => <ItemCard key={item.id} item={item} onSelect={setSelectedItem} onStatusChange={updateStatus} onDelete={deleteItem} canClaim={Boolean(authUser)} onClaim={setClaimItem} />)}</div> : <p className="empty-state">No lost reports are available for matching. Check back after a report is submitted.</p>}
          </section>

          <section className="page-view my-reports-page" hidden={activePage !== 'my-reports'}>
            <div className="page-heading"><div><p className="eyebrow">YOUR ACTIVITY</p><h1>My Reports</h1><p>Personal report ownership isn’t exposed by the current API.</p></div><button className="button button-primary" type="button" onClick={() => navigateTo('report-lost')}>＋ Create a report</button></div>
            {authUser ? (
              <div className="dashboard-panel ownership-notice">
                <span className="notice-icon" aria-hidden="true">i</span>
                <div><h2>Personal report history isn’t available yet</h2><p>The current item API does not expose report ownership, so this page can’t reliably identify which community reports belong to your account. Your reports are still available in the Lost &amp; Found board, and your claims appear in Claims.</p><button className="button button-outline" type="button" onClick={() => navigateTo('lost-found')}>Browse community reports</button></div>
              </div>
            ) : <div className="dashboard-panel ownership-notice"><div><h2>Sign in to view your activity</h2><p>Log in to access account-related features.</p><button className="button button-primary" type="button" onClick={() => navigateTo('profile')}>Go to sign in</button></div></div>}
          </section>

          <section className="page-view claims-page" hidden={activePage !== 'claims'}>
            {authUser?.role === 'ADMIN' ? (
              <section className="claims-management" aria-labelledby="admin-claims-title">
                <div className="page-heading"><div><p className="eyebrow">ADMINISTRATION</p><h1 id="admin-claims-title">Claim management</h1><p>Review ownership claims and confirm returned items.</p></div><button className="button button-light" type="button" onClick={loadAdminClaims} disabled={adminClaimsLoading}>{adminClaimsLoading ? 'Refreshing…' : 'Refresh claims'}</button></div>
                {claimStatusError && <div className="message message-error" role="alert">{claimStatusError}</div>}
                {adminClaimsError && <div className="message message-error" role="alert">{adminClaimsError}</div>}
                {adminClaimsLoading ? <p className="empty-state">Loading claims…</p> : adminClaims.length === 0 ? <p className="empty-state">No claims have been submitted for the application’s items.</p> : <div className="admin-claim-list">{adminClaims.map((claim) => (
                  <article className="admin-claim-card" key={claim.id}>
                    <div className="admin-claim-heading"><h3>Claim #{claim.id}</h3><span className={`status-badge ${claim.status.toLowerCase()}`}>{claim.status}</span></div>
                    <dl className="admin-claim-details"><div><dt>Item ID</dt><dd>{claim.itemId}</dd></div><div><dt>Linked LOST Item ID</dt><dd>{claim.lostItemId ?? 'Not linked (legacy claim)'}</dd></div><div><dt>Claimant/User ID</dt><dd>{claim.userId}</dd></div><div><dt>Created</dt><dd>{formatDateTime(claim.createdAt)}</dd></div><div className="admin-claim-verification"><dt>Verification Details</dt><dd>{claim.verificationDetails}</dd></div></dl>
                    {claim.status === 'PENDING' && <div className="claim-actions"><button className="button button-primary" type="button" onClick={() => updateClaimStatus(claim.id, 'APPROVED')} disabled={claimStatusBusyId !== null}>{claimStatusBusyId === claim.id ? 'Processing…' : 'Approve'}</button><button className="button button-danger" type="button" onClick={() => updateClaimStatus(claim.id, 'REJECTED')} disabled={claimStatusBusyId !== null}>{claimStatusBusyId === claim.id ? 'Processing…' : 'Reject'}</button></div>}
                    {claim.itemStatus === 'CLAIMED' && <div className="claim-actions"><button className="button button-primary" type="button" onClick={() => markItemResolved(claim.itemId)} disabled={resolvedItemBusyId !== null}>{resolvedItemBusyId === claim.itemId ? 'Updating…' : 'Mark as Resolved'}</button></div>}
                  </article>
                ))}</div>}
              </section>
            ) : authUser ? (
              <section className="dashboard-panel user-claims-panel">
                <div className="panel-heading"><div><p className="eyebrow">YOUR ACTIVITY</p><h1>My claims</h1><p>Track the status of your submitted ownership claims.</p></div><button className="button button-light" type="button" onClick={loadUserClaims} disabled={userClaimsLoading}>{userClaimsLoading ? 'Refreshing…' : 'Refresh claims'}</button></div>
                {userClaimsError && <p className="message message-error" role="alert">{userClaimsError}</p>}
                {userClaimsLoading ? <p className="empty-state">Loading your claims…</p> : userClaims.length ? <div className="user-claim-list">{userClaims.map((claim) => <article className="user-claim-card" key={claim.id}><div><span className="claim-reference">CLAIM #{claim.id}</span><h2>Found item #{claim.itemId}</h2><p>{claim.verificationDetails}</p><small>Submitted {formatDateTime(claim.createdAt)}</small></div><span className={`status-badge ${claim.status.toLowerCase()}`}>{claim.status}</span></article>)}</div> : <p className="empty-state">You haven’t submitted any claims yet. Browse found items and select Claim Item to get started.</p>}
              </section>
            ) : <div className="dashboard-panel auth-prompt"><h2>Sign in to view claims</h2><p>Your claims are only available to the account that submitted them.</p><button className="button button-primary" type="button" onClick={() => navigateTo('profile')}>Go to sign in</button></div>}
          </section>

          <section className="page-view notifications-page" hidden={activePage !== 'notifications'}>
            {authUser ? <NotificationBell key={`page-${authUser.id}`} userId={authUser.id} forceOpen /> : <div className="dashboard-panel auth-prompt"><h2>Sign in to view notifications</h2><p>Notifications are associated with your account.</p><button className="button button-primary" type="button" onClick={() => navigateTo('profile')}>Go to sign in</button></div>}
          </section>

          <section className="page-view profile-page" hidden={activePage !== 'profile'}>
            {authUser ? (
              <div className="profile-layout">
                <div className="page-heading"><div><p className="eyebrow">ACCOUNT SETTINGS</p><h1>Your profile</h1><p>Account details and session management.</p></div></div>
                <section className="dashboard-panel profile-card"><div className="profile-avatar">{authUser.name?.slice(0, 1).toUpperCase()}</div><div><p className="eyebrow">SIGNED IN ACCOUNT</p><h2>{authUser.name}</h2><p>{authUser.email}</p><span className="role-badge">{authUser.role}</span></div></section>
                <button className="button button-danger" type="button" onClick={handleLogout}>Log out of this browser</button>
              </div>
            ) : (
              <div className="auth-layout">
                <div className="auth-story"><span className="auth-story-mark">CF</span><p className="eyebrow">CAMPUS FIND</p><h1>Good things find their way back.</h1><p>Sign in to submit reports, track claims, and help reunite lost belongings with their owners.</p><div className="auth-story-points"><span>✓ Report lost and found items</span><span>✓ Follow your claims</span><span>✓ Get useful notifications</span></div></div>
                <AuthPanel user={authUser} onLogin={handleLogin} onLogout={handleLogout} />
              </div>
            )}
          </section>

          <section className="page-view report-page" hidden={activePage !== 'report-lost'}>
            <div className="page-heading"><div><p className="eyebrow">HELP FIND WHAT’S MISSING</p><h1>Report a lost item</h1><p>Share clear details so your community can help.</p></div><span className="type-badge lost">LOST</span></div>
            <div className="dashboard-panel report-panel"><ReportForm type="LOST" onSubmit={createReport} busy={busy} /></div>
          </section>
          <section className="page-view report-page" hidden={activePage !== 'report-found'}>
            <div className="page-heading"><div><p className="eyebrow">HELP SOMETHING GET HOME</p><h1>Report a found item</h1><p>Provide helpful details while keeping unique identifying information private.</p></div><span className="type-badge found">FOUND</span></div>
            <div className="dashboard-panel report-panel"><ReportForm type="FOUND" onSubmit={createReport} busy={busy} /></div>
          </section>

          {selectedItem && <section className="details-panel global-details-panel" aria-labelledby="details-title">
            <div className="section-heading"><div><p className="eyebrow">REPORT DETAILS</p><h2 id="details-title">{selectedItem.title}</h2></div><button className="button button-light" type="button" onClick={() => setSelectedItem(null)}>Close</button></div>
            <p>{selectedItem.description}</p>
            <dl className="details-grid"><div><dt>Type</dt><dd>{selectedItem.type}</dd></div><div><dt>Status</dt><dd>{selectedItem.status}</dd></div><div><dt>Category</dt><dd>{selectedItem.category}</dd></div><div><dt>Location</dt><dd>{selectedItem.location}</dd></div><div><dt>Date</dt><dd>{formatDate(selectedItem.date)}</dd></div><div><dt>Color / brand</dt><dd>{[selectedItem.color, selectedItem.brand].filter(Boolean).join(' / ') || 'Not provided'}</dd></div><div><dt>Contact</dt><dd>{selectedItem.contactName} · {selectedItem.contactPhone}</dd></div></dl>
            {authUser && selectedItem.type === 'FOUND' && selectedItem.status === 'ACTIVE' && <button className="button button-outline" type="button" onClick={() => setClaimItem(selectedItem)}>Claim Item</button>}
            <DuplicateDetectionPanel item={selectedItem} />
          </section>}

          {claimItem && <ClaimDialog item={claimItem} busy={claimBusy} onCancel={() => setClaimItem(null)} onSubmit={submitClaim} />}
        </main>
        <footer className="workspace-footer"><span className="brand-mark">CF</span><p>Campus Find · Helping belongings find their way home.</p></footer>
      </div>
    </div>
  )
}

export default App

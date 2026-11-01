import { useStockSearch } from '../hooks/useStockSearch.js'

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  )
}

export default function StockSearch({ selected, onSelect, searchStocks }) {
  const {
    query, items, open, searching, error,
    setQuery, setOpen, setError, reset,
  } = useStockSearch(searchStocks, selected.symbol)

  const selectStock = (stock) => {
    reset(stock.symbol)
    onSelect(stock)
  }

  const handleSubmit = (event) => {
    event.preventDefault()
    if (!query.trim()) {
      setError('请输入证券代码或名称')
      setOpen(true)
      return
    }
    const exact = items.find((item) => item.symbol.toLowerCase() === query.trim().toLowerCase())
    if (exact || items[0]) {
      selectStock(exact || items[0])
    } else {
      setOpen(true)
      if (!searching && !error) setError('未找到匹配证券，请检查代码或名称')
    }
  }

  return (
    <div
      className="stock-search"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}
    >
      <form onSubmit={handleSubmit} role="search">
        <SearchIcon />
        <input
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
            setError('')
          }}
          onFocus={() => setOpen(true)}
          placeholder="证券代码 / 名称 / 拼音"
          aria-label="搜索证券"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls="stock-results"
          role="combobox"
          autoComplete="off"
          spellCheck="false"
        />
        {searching && <span className="search-loader" aria-label="正在搜索" />}
        <button type="submit">查询</button>
      </form>

      {open && (items.length > 0 || error || (!searching && query.trim())) && (
        <div className="search-results" id="stock-results" role="listbox">
          {items.map((item) => (
            <button
              type="button"
              role="option"
              aria-selected={item.secid === selected.secid}
              key={item.secid}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => selectStock(item)}
            >
              <span className="result-symbol">{item.symbol}</span>
              <span className="result-name">{item.name}</span>
              <small>{item.market}</small>
            </button>
          ))}
          {!searching && !error && items.length === 0 && <p>未找到匹配证券，请尝试完整代码</p>}
          {error && <p className="search-error">{error}</p>}
        </div>
      )}
    </div>
  )
}

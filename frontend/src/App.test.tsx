import {render,screen} from '@testing-library/react'
import {MemoryRouter} from 'react-router-dom'
import {App} from './App'

test('shows login when there is no session',()=>{sessionStorage.clear();localStorage.clear();render(<MemoryRouter initialEntries={['/login']}><App/></MemoryRouter>);expect(screen.getByRole('heading',{name:'OpsMind'})).toBeInTheDocument();expect(screen.getByRole('button',{name:'Sign in'})).toBeInTheDocument()})
